let apps = [];

async function loadApps() {
    apps = await apiGet('/api/admin/apps');
    render();
}

function render() {
    const keyword = document.getElementById('search').value.trim().toLowerCase();
    const status = document.getElementById('status').value;
    const rows = apps.filter(app => {
        const matchKeyword = !keyword
            || app.name.toLowerCase().includes(keyword)
            || (app.env || '').toLowerCase().includes(keyword);
        const matchStatus = !status
            || (status === 'ONLINE' && app.onlineInstances > 0)
            || (status === 'OFFLINE' && app.onlineInstances === 0);
        return matchKeyword && matchStatus;
    });

    document.getElementById('appRows').innerHTML = rows.map(app => {
        const online = app.onlineInstances > 0;
        return '<tr>'
            + '<td>' + escapeHtml(app.name) + '</td>'
            + '<td>' + escapeHtml(app.env) + '</td>'
            + '<td><span class="badge ' + (online ? '' : 'offline') + '">'
            + (online ? '在线' : '离线') + '</span> '
            + app.onlineInstances + '/' + app.totalInstances + '</td>'
            + '<td>' + app.interfaceCount + '</td>'
            + '<td>' + formatTime(app.lastHeartbeatAt) + '</td>'
            + '<td><a href="/app-detail.html?appId=' + encodeURIComponent(app.appId) + '">查看</a></td>'
            + '</tr>';
    }).join('');
}

async function generateScaffold() {
    const body = {
        systemName: document.getElementById('scaffoldName').value.trim(),
        port: Number(document.getElementById('scaffoldPort').value) || 18080,
        env: document.getElementById('scaffoldEnv').value.trim() || 'dev'
    };
    if (!body.systemName) {
        document.getElementById('scaffoldInfo').textContent = '请先填写系统名称';
        return;
    }
    const info = document.getElementById('scaffoldInfo');
    info.textContent = '正在生成...';
    try {
        const response = await fetch('/api/admin/scaffolds', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            throw new Error('HTTP ' + response.status + '：' + (await response.text()).substring(0, 300));
        }
        const blob = await response.blob();
        const disposition = response.headers.get('Content-Disposition') || '';
        const matched = disposition.match(/filename="?([^";]+)"?/);
        const fileName = matched ? matched[1] : 'generated-app.zip';
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);

        info.textContent = '生成成功：' + fileName
            + '\nappId：' + response.headers.get('X-Generated-App-Id')
            + '\ntoken：' + response.headers.get('X-Generated-Token')
            + '\n解压后执行 mvn clean package && java -jar target/*.jar 即可自动注册。';
        loadApps();
    } catch (e) {
        info.textContent = '生成失败：' + e.message;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadApps().catch(e => {
        document.getElementById('appRows').innerHTML =
            '<tr><td colspan="6" class="error">加载失败：' + escapeHtml(e.message) + '</td></tr>';
    });
    document.getElementById('refresh').onclick = () => loadApps().catch(console.error);
    document.getElementById('search').oninput = render;
    document.getElementById('status').onchange = render;
    document.getElementById('generateApp').onclick = () => {
        document.getElementById('scaffoldModal').style.display = 'flex';
    };
    document.getElementById('scaffoldClose').onclick = () => {
        document.getElementById('scaffoldModal').style.display = 'none';
    };
    document.getElementById('scaffoldSubmit').onclick = generateScaffold;
});
