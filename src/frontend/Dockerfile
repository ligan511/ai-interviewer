# 多阶段：先用 Node 构建，再用 nginx 托管
FROM node:20-alpine AS builder
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /build/dist /usr/share/nginx/html
# 配置 SPA 路由 + 反代后端 API 与 uploads
RUN cat > /etc/nginx/conf.d/default.conf <<'EOF'
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # SPA fallback：所有非文件请求都回到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 反代后端 API
    location /api/ {
        proxy_pass http://backend:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 反代上传文件访问
    location /uploads/ {
        proxy_pass http://backend:8081;
        proxy_set_header Host $host;
    }
}
EOF
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
