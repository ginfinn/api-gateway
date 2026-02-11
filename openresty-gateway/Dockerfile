FROM openresty/openresty:1.21.4.3-2-alpine

# Установка lua-resty-jwt и зависимостей
RUN opm get cdbattags/lua-resty-jwt \
    && opm get openresty/lua-resty-string

# Конфигурация
COPY nginx/nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY lua /usr/local/openresty/nginx/lua

EXPOSE 443

CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
