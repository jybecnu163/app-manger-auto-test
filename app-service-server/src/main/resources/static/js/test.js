const appId = new URLSearchParams(location.search).get('appId');
let interfaces = [];
let current = null;
let currentCases = [];
let appCases = [];
let activeTab = 'interfaces';

async function init() {
    if (!appId) {
        document.getElementById('interfaceDetail').innerHTML =
            '<p class="error">缺少 appId 参数</p>';
        return;
    }
    const baseUrl = '/api/admin/apps/' + encodeURIComponent(appId);
    let detail;
    try {
        detail = await apiGet(baseUrl);
    } catch (e) {
        throw new Error('系统详情加载失败：' + e.message);
    }

    let list = [];
    let listError = null;
    try {
        list = await apiGet(baseUrl + '/interfaces');
    } catch (e) {
        listError = e.message;
    }

    document.getElementById('instancesBody').innerHTML = detail.instances && detail.instances.length
        ? detail.instances.map(instance =>
            '<tr>'
            + '<td>' + escapeHtml(instance.instanceKey) + '</td>'
            + '<td>' + escapeHtml(instance.ip) + ':' + instance.port + '</td>'
            + '<td>' + escapeHtml(instance.hostname) + '</td>'
            + '<td>' + formatTime(instance.startTime) + '</td>'
            + '</tr>').join('')
        : '<tr><td colspan="4" class="muted">暂无实例</td></tr>';

    interfaces = list;
    renderInterfaces();
    bindTabs();
    loadAppCases().catch(console.error);
    if (listError) {
        document.getElementById('interfaceDetail').innerHTML =
            '<p class="error">接口列表加载失败：' + escapeHtml(listError) + '</p>';
    }
}

function bindTabs() {
    document.getElementById('tabInterfaces').onclick = () => switchTab('interfaces');
    document.getElementById('tabCases').onclick = () => switchTab('cases');
    const replayAllButton = document.getElementById('replayAllCases');
    if (replayAllButton) replayAllButton.onclick = replayAllCases;
}

function switchTab(tab) {
    activeTab = tab;
    document.getElementById('tabInterfaces').classList.toggle('active', tab === 'interfaces');
    document.getElementById('tabCases').classList.toggle('active', tab === 'cases');
    document.getElementById('interfaces').style.display = tab === 'interfaces' ? '' : 'none';
    const toolbar = document.getElementById('caseToolbar');
    if (toolbar) toolbar.style.display = tab === 'cases' ? '' : 'none';
    document.getElementById('caseList').style.display = tab === 'cases' ? '' : 'none';
    if (tab === 'cases') {
        renderCaseList();
    }
}

function renderInterfaces() {
    document.getElementById('interfaces').innerHTML = interfaces.map((api, index) =>
        '<div class="interface-item" data-index="' + index + '">'
        + '<span class="badge method">' + escapeHtml(api.method) + '</span>'
        + '<span class="path">' + escapeHtml(api.path) + '</span>'
        + '<span class="op">' + escapeHtml(api.controller || '')
        + (api.operation ? ' · ' + escapeHtml(api.operation) : '') + '</span>'
        + '</div>').join('');

    document.querySelectorAll('.interface-item').forEach(node => {
        node.onclick = () => selectInterface(Number(node.dataset.index), node);
    });
}

function selectInterface(index, node) {
    currentCases = [];
    document.querySelectorAll('.interface-item').forEach(item => item.classList.remove('active'));
    node.classList.add('active');
    renderTestPanel(interfaces[index], null);
}

function renderTestPanel(api, presetCase) {
    current = api;
    const schema = safeParse(api.requestSchema) || {};
    const values = presetCase ? valuesFromCase(presetCase) : null;
    document.getElementById('interfaceDetail').innerHTML =
        '<h3><span class="badge method">' + escapeHtml(api.method) + '</span> '
        + escapeHtml(api.path) + '</h3>'
        + '<p class="muted">' + escapeHtml(api.controller || '')
        + (api.operation ? ' · ' + escapeHtml(api.operation) : '') + '</p>'
        + '<details><summary>请求 Schema</summary><pre>' + escapeHtml(pretty(api.requestSchema)) + '</pre></details>'
        + '<details><summary>响应 Schema</summary><pre>' + escapeHtml(pretty(api.responseSchema)) + '</pre></details>'
        + '<div class="case-bar">'
        + '<select id="caseSelect"><option value="">自定义（当前表单）</option></select>'
        + '<button id="replayCase" class="secondary">一键回放</button>'
        + '<button id="saveCase" class="secondary">保存为用例</button>'
        + '<button id="deleteCase" class="secondary">删除用例</button>'
        + '</div>'
        + '<div id="schemaForm" class="schema-form"></div>'
        + '<button id="sendTest">发送请求</button>'
        + '<div id="response" class="response-box"></div>';

    renderSchemaForm(schema, values);
    document.getElementById('sendTest').onclick = () => sendTest();
    document.getElementById('replayCase').onclick = replayCase;
    document.getElementById('saveCase').onclick = saveCase;
    document.getElementById('deleteCase').onclick = deleteCase;
    loadCases(api.id, !presetCase).then(() => {
        if (presetCase) {
            const index = currentCases.findIndex(item => item.id === presetCase.id);
            if (index >= 0) {
                document.getElementById('caseSelect').value = String(index);
            }
        }
    }).catch(console.error);
}

async function loadAppCases() {
    appCases = await apiGet('/api/admin/apps/' + encodeURIComponent(appId) + '/cases');
    renderCaseList();
}

function caseSummary(testCase) {
    const parts = [];
    const pathParams = safeParse(testCase.pathParams) || {};
    Object.keys(pathParams).forEach(key => parts.push(key + '=' + pathParams[key]));
    const queryParams = safeParse(testCase.queryParams) || {};
    Object.keys(queryParams).forEach(key => parts.push(key + '=' + queryParams[key]));
    const body = safeParse(testCase.body);
    if (body && typeof body === 'object') {
        parts.push('body=' + JSON.stringify(body));
    } else if (body) {
        parts.push('body=' + body);
    }
    const text = parts.join(', ') || '无参数';
    return text.length > 160 ? text.substring(0, 160) + '...' : text;
}

function renderCaseList() {
    const list = document.getElementById('caseList');
    if (!appCases.length) {
        list.innerHTML = '<p class="muted" style="padding:12px">暂无测试用例</p>';
        return;
    }
    list.innerHTML = appCases.map((testCase, index) =>
        '<div class="case-item" data-index="' + index + '">'
        + '<div class="case-title">' + escapeHtml(testCase.name)
        + '<span class="source-badge ' + (testCase.source === 'AUTO' ? 'auto' : '') + '">'
        + (testCase.source === 'AUTO' ? '预设' : '已保存') + '</span></div>'
        + '<div class="case-api"><span class="badge method">' + escapeHtml(testCase.interfaceMethod || 'API')
        + '</span> ' + escapeHtml(testCase.interfacePath || '') + ' · '
        + escapeHtml(testCase.interfaceName || '') + '</div>'
        + '<span class="case-summary">' + escapeHtml(caseSummary(testCase)) + '</span>'
        + '</div>').join('');
    list.querySelectorAll('.case-item').forEach(node => {
        node.onclick = () => selectAppCase(Number(node.dataset.index), node);
    });
}

function selectAppCase(index, node) {
    const testCase = appCases[index];
    const api = interfaces.find(item => item.id === testCase.interfaceId);
    current = api || current;
    document.querySelectorAll('.case-item').forEach(item => item.classList.remove('active'));
    node.classList.add('active');
    document.getElementById('interfaceDetail').innerHTML =
        '<h3>' + escapeHtml(testCase.name)
        + '<span class="source-badge ' + (testCase.source === 'AUTO' ? 'auto' : '') + '">'
        + (testCase.source === 'AUTO' ? '预设用例' : '已保存用例') + '</span></h3>'
        + '<p class="muted"><span class="badge method">' + escapeHtml(testCase.interfaceMethod || 'API')
        + '</span> ' + escapeHtml(testCase.interfacePath || '')
        + (testCase.interfaceName ? ' · ' + escapeHtml(testCase.interfaceName) : '') + '</p>'
        + '<details open><summary>用例参数</summary><pre>'
        + escapeHtml(JSON.stringify({
            pathParams: safeParse(testCase.pathParams) || {},
            queryParams: safeParse(testCase.queryParams) || {},
            headers: safeParse(testCase.headers) || {},
            body: safeParse(testCase.body)
        }, null, 2)) + '</pre></details>'
        + '<div class="case-bar">'
        + '<button id="replayDirect">一键回放</button>'
        + '<button id="testSingle" class="secondary">单用例测试</button>'
        + '<button id="deleteAppCase" class="secondary">删除用例</button>'
        + '</div>'
        + '<div id="statsBox" class="response-box"></div>'
        + '<div id="response" class="response-box"></div>';
    document.getElementById('replayDirect').onclick = () => replayDirectCase(testCase);
    document.getElementById('testSingle').onclick = () => testSingleCase(testCase);
    document.getElementById('deleteAppCase').onclick = () => deleteAppCase(testCase);
    showStats(testCase.interfaceId);
}

function testSingleCase(testCase) {
    const api = interfaces.find(item => item.id === testCase.interfaceId);
    if (!api) {
        alert('未找到该用例对应的接口，请刷新页面');
        return;
    }
    switchTab('interfaces');
    const index = interfaces.findIndex(item => item.id === api.id);
    const node = document.querySelectorAll('.interface-item')[index];
    if (node) node.classList.add('active');
    renderTestPanel(api, testCase);
}

async function deleteAppCase(testCase) {
    await apiDelete('/api/admin/apps/' + encodeURIComponent(appId) + '/interfaces/'
        + testCase.interfaceId + '/cases/' + testCase.id);
    await loadAppCases();
    document.getElementById('interfaceDetail').innerHTML =
        '<p class="muted">用例已删除，请选择其他用例。</p>';
}

async function replayAllCases() {
    const button = document.getElementById('replayAllCases');
    const status = document.getElementById('replayAllStatus');
    if (button) {
        button.disabled = true;
        button.textContent = '回放中...';
    }
    if (status) status.textContent = '回放中...';
    document.getElementById('interfaceDetail').innerHTML =
        '<p class="muted">正在批量回放所有用例，请稍候...</p>';
    try {
        const result = await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/cases/replay-all', {});
        if (status) status.textContent = '已完成 ' + result.executed + '/' + result.totalCases + ' 个用例';
        document.getElementById('interfaceDetail').innerHTML = batchResultHtml(result);
        await loadAppCases();
    } catch (e) {
        if (status) status.textContent = '回放失败';
        document.getElementById('interfaceDetail').innerHTML =
            '<p class="error">批量回放失败：' + escapeHtml(e.message) + '</p>';
    } finally {
        if (button) {
            button.disabled = false;
            button.textContent = '回放所有用例';
        }
    }
}

function batchResultHtml(result) {
    const card = (label, value) =>
        '<div class="stat-card"><div class="label">' + label + '</div><div class="value">'
        + escapeHtml(value) + '</div></div>';
    const rows = (result.details || []).map(item =>
        '<tr>'
        + '<td>' + escapeHtml(item.caseName || '') + '</td>'
        + '<td><span class="badge method">' + escapeHtml(item.interfaceMethod || 'API') + '</span> '
        + escapeHtml(item.interfacePath || '') + '</td>'
        + '<td class="' + (item.success ? 'batch-ok' : 'batch-fail') + '">'
        + (item.success ? '成功' : '失败') + '</td>'
        + '<td>' + (item.status === undefined || item.status === null ? '-' : item.status) + '</td>'
        + '<td>' + (item.costMs || 0) + ' ms</td>'
        + '<td>' + escapeHtml(item.error || item.bodyPreview || '') + '</td>'
        + '</tr>').join('');

    return '<h3>批量回放统计</h3>'
        + '<p class="muted">完成时间：' + formatTime(result.finishedAt) + '</p>'
        + '<div class="stats-grid">'
        + card('用例总数', result.totalCases + ' 个')
        + card('已执行', result.executed + ' 个')
        + card('成功 / 失败', result.succeeded + ' / ' + result.failed)
        + card('跳过', result.skipped + ' 个')
        + card('成功率', result.successRate + '%')
        + card('平均耗时', result.avgCostMs + ' ms')
        + card('最快 / 最慢', result.minCostMs + ' / ' + result.maxCostMs + ' ms')
        + card('累计耗时', result.totalCostMs + ' ms')
        + '</div>'
        + '<table class="batch-table">'
        + '<thead><tr><th>用例</th><th>接口</th><th>结果</th><th>状态码</th><th>耗时</th><th>响应摘要 / 错误</th></tr></thead>'
        + '<tbody>' + rows + '</tbody></table>';
}

function safeParse(text) {
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch (e) {
        return null;
    }
}

function pretty(text) {
    const value = safeParse(text);
    return value === null ? (text || '') : JSON.stringify(value, null, 2);
}

function typeLabel(node) {
    if (typeof node === 'string') return node;
    if (!node || typeof node !== 'object') return 'string';
    if (node.type === 'array') return 'array<' + typeLabel(node.items) + '>';
    if (node.type === 'enum') return 'enum[' + (node.values || []).join(', ') + ']';
    return node.type || 'string';
}

function renderSchemaForm(schema, values) {
    const source = values || {};
    const required = schema.required || {};
    const html = [];

    ['pathParams', 'queryParams', 'headers'].forEach(group => {
        const fields = schema[group] || {};
        const requiredList = group === 'pathParams'
            ? Object.keys(fields)
            : (required[group] || []);
        Object.keys(fields).forEach(name => {
            html.push(renderField(group, name, fields[name], requiredList.indexOf(name) >= 0,
                (source[group] || {})[name]));
        });
    });

    const body = schema.body || {};
    if (body.properties && Object.keys(body.properties).length > 0) {
        const requiredBody = required.body || [];
        Object.keys(body.properties).forEach(name => {
            html.push(renderField('body', name, body.properties[name], requiredBody.indexOf('body') >= 0,
                (source.body || {})[name]));
        });
    } else {
        const bodyValue = typeof source.body === 'string' ? source.body : '';
        html.push('<div class="field"><label>Body <span class="type">'
            + escapeHtml(typeLabel(body)) + '</span></label>'
            + '<textarea id="bodyRaw" data-group="bodyRaw" placeholder=\'{}\'>'
            + escapeHtml(bodyValue) + '</textarea></div>');
    }

    document.getElementById('schemaForm').innerHTML = html.join('');
}

function renderField(group, name, node, isRequired, value) {
    const type = escapeHtml(typeLabel(node));
    const requiredMark = isRequired ? ' <span class="req">必填</span>' : '';
    let control;
    if (node && node.type === 'enum') {
        const options = (node.values || []).map(item =>
            '<option value="' + escapeHtml(item) + '"'
            + (String(value || '') === String(item) ? ' selected' : '') + '>'
            + escapeHtml(item) + '</option>').join('');
        control = '<select data-group="' + group + '" data-name="' + escapeHtml(name) + '">' + options + '</select>';
    } else {
        control = '<input type="text" data-group="' + group + '" data-name="' + escapeHtml(name)
            + '" value="' + escapeHtml(value === undefined || value === null ? '' : value)
            + '" placeholder="' + type + '">';
    }
    return '<div class="field"><label>' + escapeHtml(name) + ' <span class="type">' + type + '</span>'
        + requiredMark + '</label>' + control + '</div>';
}

function valuesFromCase(testCase) {
    return {
        pathParams: safeParse(testCase.pathParams) || {},
        queryParams: safeParse(testCase.queryParams) || {},
        headers: safeParse(testCase.headers) || {},
        body: safeParse(testCase.body) || {}
    };
}

function collectPayload() {
    const payload = {
        interfaceId: current.id,
        pathParams: {},
        queryParams: {},
        headers: {},
        body: null
    };
    const bodyObject = {};
    document.querySelectorAll('#schemaForm [data-group]').forEach(input => {
        const group = input.dataset.group;
        const name = input.dataset.name;
        const value = input.value.trim();
        if (!value) return;
        if (group === 'body') {
            bodyObject[name] = value;
        } else if (group === 'bodyRaw') {
            payload.body = value;
        } else {
            payload[group][name] = value;
        }
    });
    if (payload.body === null && Object.keys(bodyObject).length > 0) {
        payload.body = JSON.stringify(bodyObject);
    }
    return payload;
}

function payloadFromCase(testCase) {
    const body = safeParse(testCase.body);
    return {
        interfaceId: testCase.interfaceId || current.id,
        pathParams: safeParse(testCase.pathParams) || {},
        queryParams: safeParse(testCase.queryParams) || {},
        headers: safeParse(testCase.headers) || {},
        body: body === null ? null : (typeof body === 'string' ? body : JSON.stringify(body))
    };
}

function fillFormFromCase(testCase) {
    const api = interfaces.find(item => item.id === testCase.interfaceId);
    renderSchemaForm(safeParse(api ? api.requestSchema : current.requestSchema) || {}, valuesFromCase(testCase));
}

async function loadCases(interfaceId, autoFill) {
    const cases = await apiGet('/api/admin/apps/' + encodeURIComponent(appId)
        + '/interfaces/' + interfaceId + '/cases');
    if (!current || current.id !== interfaceId) return;
    currentCases = cases;
    const select = document.getElementById('caseSelect');
    select.innerHTML = '<option value="">自定义（当前表单）</option>' + cases.map((item, index) =>
        '<option value="' + index + '">' + (item.source === 'AUTO' ? '[预设] ' : '[已保存] ')
        + escapeHtml(item.name) + '</option>').join('');
    if (autoFill && cases.length > 0) {
        select.value = '0';
        fillFormFromCase(cases[0]);
    }
}

function selectedCase() {
    const value = document.getElementById('caseSelect').value;
    return value === '' ? null : currentCases[Number(value)];
}

function replayCase() {
    const testCase = selectedCase();
    if (!testCase) {
        alert('请先选择一个用例');
        return;
    }
    fillFormFromCase(testCase);
    sendTest(payloadFromCase(testCase));
}

function replayDirectCase(testCase) {
    sendTest(payloadFromCase(testCase));
}

async function saveCase() {
    const name = prompt('用例名称：', '测试-' + new Date().toLocaleTimeString('zh-CN', { hour12: false }));
    if (name === null) return;
    const payload = collectPayload();
    await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/interfaces/' + current.id + '/cases', {
        name: name,
        pathParams: payload.pathParams,
        queryParams: payload.queryParams,
        headers: payload.headers,
        body: payload.body
    });
    await Promise.all([loadCases(current.id, false), loadAppCases()]);
}

async function deleteCase() {
    const testCase = selectedCase();
    if (!testCase) {
        alert('请先选择一个用例');
        return;
    }
    await apiDelete('/api/admin/apps/' + encodeURIComponent(appId) + '/interfaces/'
        + current.id + '/cases/' + testCase.id);
    await Promise.all([loadCases(current.id, false), loadAppCases()]);
}

function ensureStatsBox() {
    let box = document.getElementById('statsBox');
    if (!box) {
        box = document.createElement('div');
        box.id = 'statsBox';
        box.className = 'response-box';
        const response = document.getElementById('response');
        if (response) response.appendChild(box);
    }
    return box;
}

function statsHtml(stats) {
    if (!stats || !stats.total) {
        return '<p class="muted">暂无测试统计</p>';
    }
    const card = (label, value) =>
        '<div class="stat-card"><div class="label">' + label + '</div><div class="value">'
        + escapeHtml(value) + '</div></div>';
    return '<h3>测试统计</h3><div class="stats-grid">'
        + card('累计测试', stats.total + ' 次')
        + card('成功 / 失败', stats.success + ' / ' + stats.failed)
        + card('成功率', stats.successRate + '%')
        + card('平均耗时', stats.avgCostMs + ' ms')
        + card('最快 / 最慢', stats.minCostMs + ' / ' + stats.maxCostMs + ' ms')
        + card('最近测试', formatTime(stats.lastTestAt))
        + '</div>';
}

async function showStats(interfaceId) {
    try {
        const stats = await apiGet('/api/admin/interfaces/' + interfaceId + '/stats');
        const box = ensureStatsBox();
        if (box) box.innerHTML = statsHtml(stats);
    } catch (e) {
        const box = ensureStatsBox();
        if (box) box.innerHTML = '<p class="error">统计加载失败：' + escapeHtml(e.message) + '</p>';
    }
}

async function sendTest(payloadOverride) {
    const box = document.getElementById('response');
    box.innerHTML = '<p class="muted">请求中...</p>';
    const payload = payloadOverride || collectPayload();
    try {
        const result = await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/test', payload);
        box.innerHTML = '<p>状态：<b>' + result.status + '</b>　耗时：' + result.costMs + ' ms'
            + '　响应大小：' + (result.body || '').length + ' 字节</p>'
            + '<details><summary>响应头</summary><pre>' + escapeHtml(JSON.stringify(result.headers, null, 2)) + '</pre></details>'
            + '<details open><summary>响应体</summary><pre>' + escapeHtml(pretty(result.body)) + '</pre></details>';
        await apiPost('/api/admin/apps/' + encodeURIComponent(appId) + '/interfaces/' + current.id + '/cases', {
            name: '测试-' + new Date().toLocaleTimeString('zh-CN', { hour12: false }),
            pathParams: payload.pathParams,
            queryParams: payload.queryParams,
            headers: payload.headers,
            body: payload.body
        });
        await Promise.all([loadCases(current.id, false), loadAppCases()]);
        await showStats(current.id);
    } catch (e) {
        box.innerHTML = '<p class="error">测试失败：' + escapeHtml(e.message) + '</p>';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    init().catch(e => {
        document.getElementById('interfaceDetail').innerHTML =
            '<p class="error">' + escapeHtml(e.message || String(e)) + '</p>'
            + '<pre>' + escapeHtml(e.stack || '') + '</pre>';
        console.error(e);
    });
});
