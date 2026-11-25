let trafficRecords = [];
let trafficLoaded = false;

function trafficQuery() {
    const params = new URLSearchParams();
    const interfaceId = document.getElementById('trafficInterface').value;
    const method = document.getElementById('trafficMethod').value;
    const status = document.getElementById('trafficStatus').value.trim();
    const traceId = document.getElementById('trafficTraceId').value.trim();
    const startTime = document.getElementById('trafficStart').value;
    const endTime = document.getElementById('trafficEnd').value;
    const onlyFailures = document.getElementById('trafficOnlyFailures').checked;
    if (interfaceId) params.set('interfaceId', interfaceId);
    if (method) params.set('method', method);
    if (status) params.set('status', status);
    if (traceId) params.set('traceId', traceId);
    if (startTime) params.set('startTime', new Date(startTime).toISOString());
    if (endTime) params.set('endTime', new Date(endTime).toISOString());
    if (onlyFailures) params.set('onlyFailures', 'true');
    return params.toString();
}

async function loadTraffic() {
    if (!appId) return;
    if (!trafficLoaded) {
        initTrafficFilters();
        trafficLoaded = true;
    }
    const box = document.getElementById('trafficTableBody');
    box.innerHTML = '<tr><td colspan="9" class="muted">加载中...</td></tr>';
    try {
        const query = trafficQuery();
        trafficRecords = await apiGet('/api/admin/apps/' + encodeURIComponent(appId)
            + '/traffic/records' + (query ? '?' + query : ''));
        renderTraffic();
    } catch (e) {
        box.innerHTML = '<tr><td colspan="9" class="error">' + escapeHtml(e.message) + '</td></tr>';
    }
}

function initTrafficFilters() {
    const select = document.getElementById('trafficInterface');
    select.innerHTML = '<option value="">全部接口</option>' + interfaces.map(api =>
        '<option value="' + api.id + '">' + escapeHtml(api.method + ' ' + api.path) + '</option>').join('');
    document.getElementById('trafficQuery').onclick = () => loadTraffic();
    document.getElementById('trafficReset').onclick = () => {
        ['trafficInterface', 'trafficMethod', 'trafficStatus', 'trafficTraceId',
            'trafficStart', 'trafficEnd'].forEach(id => { document.getElementById(id).value = ''; });
        document.getElementById('trafficOnlyFailures').checked = false;
        loadTraffic();
    };
}

function renderTraffic() {
    const box = document.getElementById('trafficTableBody');
    if (!trafficRecords.length) {
        box.innerHTML = '<tr><td colspan="9" class="muted">暂无录制流量</td></tr>';
        return;
    }
    box.innerHTML = trafficRecords.map((record, index) =>
        '<tr>'
        + '<td>' + formatTime(record.createdAt) + '</td>'
        + '<td>' + escapeHtml(record.method) + '</td>'
        + '<td class="traffic-path">' + escapeHtml(record.path) + '</td>'
        + '<td>' + escapeHtml(record.interfaceId || '-') + '</td>'
        + '<td>' + escapeHtml(record.responseStatus) + '</td>'
        + '<td class="' + (record.success ? 'batch-ok' : 'batch-fail') + '">'
        + (record.success ? '成功' : '失败') + '</td>'
        + '<td>' + escapeHtml(record.costMs) + 'ms</td>'
        + '<td class="traffic-trace">' + escapeHtml(record.traceId || '-') + '</td>'
        + '<td><button class="secondary traffic-replay" data-index="' + index + '">Dry-Run 回放</button></td>'
        + '</tr>').join('');
    document.querySelectorAll('.traffic-replay').forEach(button => {
        button.onclick = () => dryRunTraffic(Number(button.dataset.index));
    });
    document.querySelectorAll('.traffic-row-detail').forEach(node => {
        node.onclick = () => showTrafficDetail(Number(node.dataset.index));
    });
    Array.from(box.querySelectorAll('tr')).forEach((row, index) => {
        if (trafficRecords[index]) {
            row.onclick = () => showTrafficDetail(index);
        }
    });
}

function prettyJson(value) {
    if (!value) return '';
    try { return JSON.stringify(JSON.parse(value), null, 2); } catch (e) { return value; }
}

function showTrafficDetail(index) {
    const record = trafficRecords[index];
    if (!record) return;
    document.getElementById('trafficDetail').innerHTML =
        '<h3><span class="badge method">' + escapeHtml(record.method) + '</span> '
        + escapeHtml(record.path) + '</h3>'
        + '<p class="muted">traceId：' + escapeHtml(record.traceId || '-')
        + ' · 时间：' + formatTime(record.createdAt)
        + ' · 耗时：' + escapeHtml(record.costMs) + 'ms</p>'
        + '<h4>请求 Headers</h4><pre>' + escapeHtml(prettyJson(record.headers)) + '</pre>'
        + '<h4>请求 Query</h4><pre>' + escapeHtml(prettyJson(record.queryParams)) + '</pre>'
        + '<h4>请求 Body</h4><pre>' + escapeHtml(prettyJson(record.body)) + '</pre>'
        + '<h4>响应 ' + escapeHtml(record.responseStatus) + '</h4><pre>'
        + escapeHtml(prettyJson(record.responseBody)) + '</pre>'
        + (record.error ? '<p class="error">错误：' + escapeHtml(record.error) + '</p>' : '');
    document.querySelectorAll('.traffic-replay').forEach(button => {
        button.textContent = Number(button.dataset.index) === index ? 'Dry-Run 回放（当前）' : 'Dry-Run 回放';
    });
}

async function dryRunTraffic(index) {
    const record = trafficRecords[index];
    if (!record || !record.interfaceId) {
        document.getElementById('replayResult').innerHTML =
            '<p class="error">该流量未关联接口，无法回放</p>';
        return;
    }
    showTrafficDetail(index);
    const box = document.getElementById('replayResult');
    box.innerHTML = '<p class="muted">正在执行 Dry-Run 回放...</p>';
    try {
        const result = await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/test', {
            interfaceId: record.interfaceId,
            replayMode: 'DRY_RUN',
            trafficRecordId: record.id
        });
        box.innerHTML = ''
            + '<div class="stats-grid">'
            + '<div class="stat-card"><div class="label">回放模式</div><div class="value">'
            + escapeHtml(result.replayMode || '-') + '</div></div>'
            + '<div class="stat-card"><div class="label">数据库已回滚</div><div class="value">'
            + (result.dbRolledBack ? '是' : '否') + '</div></div>'
            + '<div class="stat-card"><div class="label">Mock 命中</div><div class="value">'
            + escapeHtml(result.mockHits) + '</div></div>'
            + '<div class="stat-card"><div class="label">Mock 未命中</div><div class="value">'
            + escapeHtml(result.mockMisses) + '</div></div>'
            + '</div>'
            + '<p class="muted">replayId：' + escapeHtml(result.replayId || '-')
            + ' · 业务状态码：' + escapeHtml(result.status) + ' · 耗时：' + escapeHtml(result.costMs) + 'ms</p>'
            + '<pre>' + escapeHtml(prettyJson(result.body)) + '</pre>';
    } catch (e) {
        box.innerHTML = '<p class="error">回放失败：' + escapeHtml(e.message) + '</p>';
    }
}
