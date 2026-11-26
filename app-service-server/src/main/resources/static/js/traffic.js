let trafficRecords = [];
let trafficLoaded = false;
let currentRecordingState = '已停止';
const selectedTrafficIds = new Set();

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
    box.innerHTML = '<tr><td colspan="10" class="muted">加载中...</td></tr>';
    try {
        const query = trafficQuery();
        trafficRecords = await apiGet('/api/admin/apps/' + encodeURIComponent(appId)
            + '/traffic/records' + (query ? '?' + query : ''));
        pruneSelectedTraffic();
        renderTraffic();
    } catch (e) {
        box.innerHTML = '<tr><td colspan="10" class="error">' + escapeHtml(e.message) + '</td></tr>';
    }
}

function recordingState(app) {
    if (!app || !app.trafficRecordEnabled) {
        return '已停止';
    }
    if (app.trafficRecordStartAt && new Date(app.trafficRecordStartAt).getTime() > Date.now()) {
        return '待开始';
    }
    return '录制中';
}

async function loadRecordingStatus() {
    try {
        const apps = await apiGet('/api/admin/apps');
        const app = apps.find(item => item.appId === appId);
        const state = recordingState(app);
        const status = document.getElementById('trafficRecordStatus');
        status.textContent = state;
        status.className = state === '录制中' ? 'recording-on'
            : state === '待开始' ? 'recording-pending' : 'recording-off';
        currentRecordingState = state;
        const toggle = document.getElementById('toggleRecording');
        toggle.textContent = state === '已停止' ? '开始录制' : '结束录制';
        toggle.className = state === '已停止' ? '' : 'secondary';
        document.getElementById('recordingStartAt').disabled = state !== '已停止';
        if (app && app.trafficRecordStartAt && state !== '录制中') {
            document.getElementById('recordingStartAt').value =
                app.trafficRecordStartAt.substring(0, 16);
        }
    } catch (e) {
        document.getElementById('trafficRecordStatus').textContent = '读取失败';
    }
}

async function setRecording(enabled) {
    const startAt = enabled ? document.getElementById('recordingStartAt').value : '';
    const body = enabled && startAt ? { startAt } : {};
    try {
        await apiPost('/api/admin/apps/' + encodeURIComponent(appId)
            + '/traffic-record/enabled?enabled=' + enabled, body);
        if (!enabled) {
            document.getElementById('recordingStartAt').value = '';
        }
        await loadRecordingStatus();
        if (!enabled) {
            await loadTraffic();
        }
    } catch (e) {
        alert('设置录制状态失败：' + e.message);
    }
}

function initTrafficFilters() {
    const select = document.getElementById('trafficInterface');
    select.innerHTML = '<option value="">全部接口</option>' + interfaces.map(api =>
        '<option value="' + api.id + '">' + escapeHtml(api.method + ' ' + api.path) + '</option>').join('');
    document.getElementById('trafficQuery').onclick = () => loadTraffic();
    document.getElementById('toggleRecording').onclick = () =>
        setRecording(currentRecordingState === '已停止');
    document.getElementById('trafficSelectAll').onchange = toggleSelectAll;
    document.getElementById('batchReplay').onclick = batchReplayTraffic;
    loadRecordingStatus();
    document.getElementById('trafficReset').onclick = () => {
        ['trafficInterface', 'trafficMethod', 'trafficStatus', 'trafficTraceId',
            'trafficStart', 'trafficEnd'].forEach(id => { document.getElementById(id).value = ''; });
        document.getElementById('trafficOnlyFailures').checked = false;
        loadTraffic();
    };
}

function pruneSelectedTraffic() {
    const validIds = new Set(trafficRecords.map(record => record.id));
    Array.from(selectedTrafficIds).forEach(id => {
        if (!validIds.has(id)) {
            selectedTrafficIds.delete(id);
        }
    });
}

function updateSelectionUI() {
    const count = selectedTrafficIds.size;
    document.getElementById('trafficSelectedCount').textContent = '已选 ' + count + ' 条';
    document.getElementById('batchReplay').disabled = count === 0;
    const selectedVisible = trafficRecords.filter(record => selectedTrafficIds.has(record.id)).length;
    const selectAll = document.getElementById('trafficSelectAll');
    selectAll.checked = trafficRecords.length > 0 && selectedVisible === trafficRecords.length;
    selectAll.indeterminate = selectedVisible > 0 && selectedVisible < trafficRecords.length;
}

function toggleSelectAll() {
    const checked = document.getElementById('trafficSelectAll').checked;
    selectedTrafficIds.clear();
    if (checked) {
        trafficRecords.forEach(record => selectedTrafficIds.add(record.id));
    }
    document.querySelectorAll('.traffic-check').forEach(checkbox => {
        checkbox.checked = checked;
    });
    updateSelectionUI();
}

function renderTraffic() {
    const box = document.getElementById('trafficTableBody');
    if (!trafficRecords.length) {
        box.innerHTML = '<tr><td colspan="10" class="muted">暂无录制流量</td></tr>';
        updateSelectionUI();
        return;
    }
    box.innerHTML = trafficRecords.map((record, index) =>
        '<tr>'
        + '<td><input type="checkbox" class="traffic-check" data-id="' + record.id + '"'
        + (selectedTrafficIds.has(record.id) ? ' checked' : '') + '></td>'
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
    document.querySelectorAll('.traffic-check').forEach(checkbox => {
        checkbox.onclick = event => event.stopPropagation();
        checkbox.onchange = () => {
            const id = Number(checkbox.dataset.id);
            if (checkbox.checked) {
                selectedTrafficIds.add(id);
            } else {
                selectedTrafficIds.delete(id);
            }
            updateSelectionUI();
        };
    });
    document.querySelectorAll('.traffic-replay').forEach(button => {
        button.onclick = event => {
            event.stopPropagation();
            dryRunTraffic(Number(button.dataset.index));
        };
    });
    Array.from(box.querySelectorAll('tr')).forEach((row, index) => {
        if (trafficRecords[index]) {
            row.onclick = () => showTrafficDetail(index);
        }
    });
    updateSelectionUI();
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

async function batchReplayTraffic() {
    const button = document.getElementById('batchReplay');
    if (!button || button.disabled) return;
    const records = trafficRecords.filter(record => selectedTrafficIds.has(record.id));
    if (!records.length) {
        alert('请先选择要回放的流量');
        return;
    }

    const originalText = button.textContent;
    button.disabled = true;
    button.textContent = '回放中...';
    const box = document.getElementById('replayResult');
    box.innerHTML = '<p class="muted">正在批量 Dry-Run 回放 ' + records.length + ' 条流量...</p>';

    const details = [];
    let successCount = 0;
    let failCount = 0;
    let skippedCount = 0;
    let mockHits = 0;
    let mockMisses = 0;

    for (const record of records) {
        const item = {
            method: record.method,
            path: record.path,
            createdAt: record.createdAt,
            outcome: '失败',
            cssClass: 'batch-fail',
            replayId: '-',
            status: '-',
            mockHits: '-',
            mockMisses: '-',
            message: ''
        };
        if (!record.interfaceId) {
            skippedCount++;
            item.outcome = '跳过';
            item.cssClass = 'muted';
            item.message = '未关联接口，无法回放';
            details.push(item);
            continue;
        }
        try {
            const result = await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/test', {
                interfaceId: record.interfaceId,
                replayMode: 'DRY_RUN',
                trafficRecordId: record.id
            });
            successCount++;
            item.outcome = '成功';
            item.cssClass = 'batch-ok';
            item.replayId = result.replayId || '-';
            item.status = result.status == null ? '-' : result.status;
            item.mockHits = result.mockHits == null ? 0 : result.mockHits;
            item.mockMisses = result.mockMisses == null ? 0 : result.mockMisses;
            item.message = result.dbRolledBack ? '数据库已回滚' : '数据库未回滚';
            mockHits += item.mockHits;
            mockMisses += item.mockMisses;
        } catch (e) {
            failCount++;
            item.message = e.message || '回放失败';
        }
        details.push(item);
    }

    try {
        box.innerHTML = ''
            + '<div class="stats-grid">'
            + '<div class="stat-card"><div class="label">选中流量</div><div class="value">'
            + records.length + '</div></div>'
            + '<div class="stat-card"><div class="label">回放成功</div><div class="value batch-ok">'
            + successCount + '</div></div>'
            + '<div class="stat-card"><div class="label">回放失败</div><div class="value batch-fail">'
            + failCount + '</div></div>'
            + '<div class="stat-card"><div class="label">跳过</div><div class="value">'
            + skippedCount + '</div></div>'
            + '<div class="stat-card"><div class="label">Mock 命中</div><div class="value">'
            + mockHits + '</div></div>'
            + '<div class="stat-card"><div class="label">Mock 未命中</div><div class="value">'
            + mockMisses + '</div></div>'
            + '</div>'
            + '<table class="batch-table"><thead><tr><th>流量</th><th>结果</th><th>replayId</th>'
            + '<th>业务状态码</th><th>Mock 命中/未命中</th><th>说明</th></tr></thead><tbody>'
            + details.map(item => '<tr>'
                + '<td>' + escapeHtml(formatTime(item.createdAt)) + '<br>'
                + escapeHtml(item.method) + ' ' + escapeHtml(item.path) + '</td>'
                + '<td class="' + item.cssClass + '">' + escapeHtml(item.outcome) + '</td>'
                + '<td>' + escapeHtml(item.replayId) + '</td>'
                + '<td>' + escapeHtml(item.status) + '</td>'
                + '<td>' + escapeHtml(item.mockHits) + ' / ' + escapeHtml(item.mockMisses) + '</td>'
                + '<td>' + escapeHtml(item.message) + '</td>'
                + '</tr>').join('')
            + '</tbody></table>';
    } finally {
        button.textContent = originalText;
        updateSelectionUI();
    }
}
