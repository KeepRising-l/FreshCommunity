# 前端镜像：Node 构建 → Nginx 托管（含 /api、/uploads 反代与 history 路由兜底）
# 构建：docker build -f docker/frontend.Dockerfile -t freshcommunity-web .
FROM node:20-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
