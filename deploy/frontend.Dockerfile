# 前端镜像：CI 中构建(node 编译 dist → nginx 内置),服务器只 pull
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# 注意：deploy/nginx.conf 由 docker-compose 挂载覆盖默认配置(含 /api 反代与 HTTPS)
