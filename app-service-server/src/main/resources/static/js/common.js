async function request(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    let json;
    try {
        json = JSON.parse(text);
    } catch (e) {
        throw new Error(url + ' → HTTP ' + response.status + ' 返回非 JSON：' + text.substring(0, 200));
    }
    if (json.code !== 0) {
        throw new Error(url + ' → ' + (json.message || ('HTTP ' + response.status + ' 请求失败')));
    }
    return json.data;
}

function apiGet(url) {
    return request(url);
}

function apiPost(url, body) {
    return request(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {})
    });
}

function apiDelete(url) {
    return request(url, { method: 'DELETE' });
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function formatTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

function parseJsonMap(text, name) {
    if (!text || !text.trim()) return {};
    const value = JSON.parse(text);
    if (typeof value !== 'object' || Array.isArray(value)) {
        throw new Error(name + ' 必须是 JSON 对象');
    }
    const result = {};
    Object.keys(value).forEach(key => { result[key] = String(value[key]); });
    return result;
}
