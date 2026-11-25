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

document.addEventListener('DOMContentLoaded', () => {
    loadApps().catch(e => {
        document.getElementById('appRows').innerHTML =
            '<tr><td colspan="6" class="error">加载失败：' + escapeHtml(e.message) + '</td></tr>';
    });
    document.getElementById('refresh').onclick = () => loadApps().catch(console.error);
    document.getElementById('search').oninput = render;
    document.getElementById('status').onchange = render;
});
