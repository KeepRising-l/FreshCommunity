package com.freshcommunity;

import com.freshcommunity.dto.AiRecommendVO;
import com.freshcommunity.entity.GroupBuy;
import com.freshcommunity.entity.GroupBuyItem;
import com.freshcommunity.entity.Product;
import com.freshcommunity.mapper.GroupBuyItemMapper;
import com.freshcommunity.mapper.ProductMapper;
import com.freshcommunity.service.AiClient;
import com.freshcommunity.service.AiRecommendService;
import com.freshcommunity.service.GroupBuyService;
import com.freshcommunity.service.ReviewAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AI 即时推荐单元测试（mock AiClient，不外呼）：候选组装、点名商品回扫、不可用报错。
 */
@DisplayName("AI 即时推荐：候选组装与点名回扫（mock LLM）")
@ExtendWith(MockitoExtension.class)
class AiRecommendServiceTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private GroupBuyService groupBuyService;
    @Mock
    private ReviewAnalysisService reviewAnalysisService;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private GroupBuyItemMapper groupBuyItemMapper;
    @Mock
    private com.freshcommunity.mapper.UserMapper userMapper;

    private AiRecommendService service;

    private final Product fuji = product(17L, "红富士苹果", "12.90");
    private final Product giant = product(19L, "甘肃花牛苹果", "9.90");
    private final Product spinach = product(3L, "新鲜菠菜", "6.90");

    @BeforeEach
    void setUp() {
        service = new AiRecommendService(productMapper, groupBuyItemMapper,
                groupBuyService, reviewAnalysisService, aiClient, userMapper);
    }

    private Product product(long id, String name, String price) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setCategory("水果");
        p.setPrice(new BigDecimal(price));
        p.setStock(50);
        p.setStatus(1);
        return p;
    }

    private void stubCandidates() {
        when(productMapper.selectList(any())).thenReturn(List.of(fuji, giant, spinach));
        GroupBuy gb = new GroupBuy();
        gb.setId(100L);
        gb.setLeaderId(2L);
        gb.setTitle("红富士苹果 · 产地直发");
        gb.setTargetCount(3);
        gb.setMemberCount(2);
        gb.setDeadline(LocalDateTime.now().plusHours(2));
        when(groupBuyService.listOngoing()).thenReturn(List.of(gb));
        com.freshcommunity.entity.User leader = new com.freshcommunity.entity.User();
        leader.setId(2L);
        leader.setPickupPointName("幸福小区便利店");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(leader));
        GroupBuyItem item = new GroupBuyItem();
        item.setGroupBuyId(100L);
        item.setProductId(17L);
        item.setPriceSnapshot(new BigDecimal("9.90"));
        when(groupBuyItemMapper.selectList(any())).thenReturn(List.of(item));
        when(reviewAnalysisService.topTags(org.mockito.ArgumentMatchers.anyList(), anyInt()))
                .thenReturn(Map.of(17L, List.of("新鲜", "实惠"), 19L, List.of("甜")));
    }

    @Test
    @DisplayName("LLM 不可用（未配 key / 调用失败）时明确报错，不做假推荐")
    void unavailableThrows() {
        when(aiClient.isAvailable()).thenReturn(false);
        assertThatThrownBy(() -> service.recommend("性价比高的苹果"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI 服务暂时不可用");

        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.chat(any(), any(), eq(false))).thenReturn(null);
        when(productMapper.selectList(any())).thenReturn(List.of(fuji));
        assertThatThrownBy(() -> service.recommend("性价比高的苹果"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("暂时没有响应");
    }

    @Test
    @DisplayName("点名回扫：长名优先命中（红富士苹果不被'苹果'截胡），卡片挂团购与拼团价")
    void picksMentionedItemsWithLongNameFirst() {
        stubCandidates();
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.chat(any(), any(), eq(false))).thenReturn(
                "推荐：红富士苹果，拼团价9.9元比零售12.9元划算，口碑说新鲜实惠。\n"
                        + "也可以看看：甘肃花牛苹果，更便宜适合做果泥。\n"
                        + "小贴士：成团还差1人，尽快下单。");

        AiRecommendVO vo = service.recommend("性价比高的苹果");

        assertThat(vo.getReply()).contains("推荐");
        assertThat(vo.getItems()).hasSize(2);
        AiRecommendVO.ItemVO first = vo.getItems().get(0);
        assertThat(first.getName()).isEqualTo("红富士苹果");
        // 卡片挂进行中团购：拼团价 9.90（来自 group_buy_item），原价为零售价
        assertThat(first.getGroupBuyId()).isEqualTo(100L);
        assertThat(first.getPrice().compareTo(new BigDecimal("9.90"))).isZero();
        assertThat(first.getOriginalPrice().compareTo(new BigDecimal("12.90"))).isZero();
        assertThat(first.getInsightTags()).containsExactly("新鲜", "实惠");
        assertThat(vo.getItems().get(1).getName()).isEqualTo("甘肃花牛苹果");
    }

    @Test
    @DisplayName("候选清单携带在团价/进度与口碑；用户需求与清单都进入 prompt")
    void candidateLinesCarryRealData() {
        stubCandidates();
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.chat(any(), any(), eq(false))).thenReturn("推荐：红富士苹果，理由。");

        service.recommend("性价比高的苹果");

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(aiClient).chat(any(), captor.capture(), eq(false));
        String prompt = captor.getValue();
        assertThat(prompt).contains("用户需求：性价比高的苹果");
        assertThat(prompt).contains("#17 红富士苹果[水果] 零售12.90元 库存50");
        assertThat(prompt).contains("在团:「红富士苹果 · 产地直发」拼团价9.90元 已参团2/3人");
        assertThat(prompt).contains("口碑:新鲜,实惠");
        assertThat(prompt).contains("#19 甘肃花牛苹果[水果] 零售9.90元");
    }
}
