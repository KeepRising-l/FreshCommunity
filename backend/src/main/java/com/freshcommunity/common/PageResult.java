package com.freshcommunity.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回结构。包一层而非直接暴露 IPage，
 * 避免前端消费 MyBatis-Plus 的内部序列化细节（orders/describe 等噪音字段）。
 */
@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <T> PageResult<T> from(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(page.getRecords());
        r.setTotal(page.getTotal());
        r.setCurrent(page.getCurrent());
        r.setSize(page.getSize());
        r.setPages(page.getPages());
        return r;
    }

    /** 分页查询后逐条转换记录类型（total 等分页元信息保持不变）。 */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        PageResult<R> r = new PageResult<>();
        r.setRecords(this.records.stream().map(mapper).toList());
        r.setTotal(this.total);
        r.setCurrent(this.current);
        r.setSize(this.size);
        r.setPages(this.pages);
        return r;
    }
}
