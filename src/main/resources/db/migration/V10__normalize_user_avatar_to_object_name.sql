-- D1-W28-17 历史 user.avatar 归一化
-- 背景：Web 端上传头像时把 <origin>/api/files/download?objectName=... 形式的绝对 URL 入库，
--      导致 host 被写死成 Web 当时的 origin（常见为 localhost:8080），Android / 其他设备访问失败。
-- 修复：把所有形如 http(s)://*/api/files/download?objectName=<value> 的 avatar
--      归一化为 <value>（即纯 objectName，相对资源标识），客户端各自拼自己的 baseUrl。
-- 注意：objectName 在 URL 中是 URL-encoded，所以归一化后还需要把 %2F 还原为 '/'。
--      objectName 字符集是 UUID + 后缀 + 单层 user-id 前缀（如 "1/abc.png"），
--      实际只会出现 '/' 被编码为 %2F，其余字符无需解码。
UPDATE users
SET avatar = replace(
        regexp_replace(
            avatar,
            '^https?://[^/]+/api/files/download\?[^#]*?objectName=([^&#]+).*$',
            '\1'
        ),
        '%2F',
        '/'
    )
WHERE avatar ~ '^https?://[^/]+/api/files/download\?[^#]*objectName=';
