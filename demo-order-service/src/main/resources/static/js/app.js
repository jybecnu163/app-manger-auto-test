async function api(url, options) {
    const response = await fetch(url, options);
    const text = await response.text();
    let data;
    try {
        data = text ? JSON.parse(text) : null;
    } catch (e) {
        data = text;
    }
    if (!response.ok) {
        const detail = data && data.message ? data.message : String(data || text).substring(0, 200);
        throw new Error('HTTP ' + response.status + '：' + detail);
    }
    return data;
}

function jsonBody(value) {
    return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(value) };
}

function fmt(value) {
    return value === null || value === undefined ? '' : JSON.stringify(value, null, 2);
}

function show(id, value, error) {
    document.getElementById(id).textContent = (error ? '错误：' + error.message : fmt(value));
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

function numberOrZero(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
}

function renderOrders(data) {
    const items = data.items || [];
    document.getElementById('listMeta').textContent =
        '共 ' + (data.total || 0) + ' 条，当前第 ' + (data.page || 1) + ' 页，每页 ' + (data.size || 10) + ' 条。';
    document.getElementById('listBody').innerHTML = items.length
        ? items.map(item =>
            '<tr><td>' + item.id + '</td>'
            + '<td>' + escapeHtml(item.orderNo) + '</td>'
            + '<td>' + escapeHtml(item.customerId) + '</td>'
            + '<td>' + escapeHtml(item.status) + '</td>'
            + '<td>' + escapeHtml(item.amount) + '</td>'
            + '<td>' + escapeHtml(item.address) + '</td>'
            + '<td><button class="secondary" data-id="' + item.id + '">查看详情</button></td></tr>').join('')
        : '<tr><td colspan="7" class="muted">暂无数据</td></tr>';
    document.querySelectorAll('#listBody button[data-id]').forEach(button => {
        button.onclick = () => {
            document.getElementById('orderId').value = button.dataset.id;
            getOrder();
        };
    });
}

async function loadList() {
    const params = new URLSearchParams();
    const customerId = document.getElementById('listCustomer').value.trim();
    const status = document.getElementById('listStatus').value;
    if (customerId) params.set('customerId', customerId);
    if (status) params.set('status', status);
    params.set('page', document.getElementById('listPage').value || '1');
    params.set('size', document.getElementById('listSize').value || '10');
    try {
        renderOrders(await api('/api/orders?' + params.toString()));
    } catch (e) {
        document.getElementById('listMeta').textContent = e.message;
    }
}

function addItemRow() {
    const row = document.createElement('div');
    row.className = 'item-row';
    row.innerHTML = '<input class="item-product-id" type="number" placeholder="1" value="1">'
        + '<input class="item-name" placeholder="商品名称" value="示例商品">'
        + '<input class="item-quantity" type="number" min="1" value="1">'
        + '<input class="item-price" type="number" step="0.01" value="99.00">'
        + '<button type="button" class="secondary remove-item">删除</button>';
    row.querySelector('.remove-item').onclick = () => row.remove();
    document.getElementById('items').appendChild(row);
}

function collectItems() {
    return Array.from(document.querySelectorAll('#items .item-row')).map(row => ({
        productId: numberOrZero(row.querySelector('.item-product-id').value),
        productName: row.querySelector('.item-name').value.trim(),
        quantity: numberOrZero(row.querySelector('.item-quantity').value),
        price: numberOrZero(row.querySelector('.item-price').value)
    })).filter(item => item.quantity > 0);
}

async function createOrder() {
    const body = {
        customerId: document.getElementById('newCustomer').value.trim(),
        address: document.getElementById('newAddress').value.trim(),
        items: collectItems()
    };
    try {
        show('createResult', await api('/api/orders', jsonBody(body)));
        loadList();
    } catch (e) {
        show('createResult', null, e);
    }
}

async function getOrder() {
    try {
        show('orderResult', await api('/api/orders/' + document.getElementById('orderId').value));
    } catch (e) {
        show('orderResult', null, e);
    }
}

async function payOrder() {
    const id = document.getElementById('orderId').value;
    const body = { payMethod: document.getElementById('payMethod').value.trim() || 'ALIPAY' };
    try {
        show('orderResult', await api('/api/orders/' + id + '/pay', jsonBody(body)));
        loadList();
    } catch (e) {
        show('orderResult', null, e);
    }
}

async function cancelOrder() {
    try {
        show('orderResult', await api('/api/orders/' + document.getElementById('orderId').value + '/cancel', jsonBody({})));
        loadList();
    } catch (e) {
        show('orderResult', null, e);
    }
}

async function deleteOrder() {
    try {
        const result = await api('/api/orders/' + document.getElementById('orderId').value, { method: 'DELETE' });
        show('orderResult', { deleted: true, message: result });
        loadList();
    } catch (e) {
        show('orderResult', null, e);
    }
}

async function runDryRun() {
    const body = { orderId: numberOrZero(document.getElementById('dryRunOrderId').value) || 1 };
    try {
        show('dryRunResult', await api('/api/orders/dryrun-demo', jsonBody(body)));
        loadAuditCount();
    } catch (e) {
        show('dryRunResult', null, e);
    }
}

async function loadAuditCount() {
    try {
        const count = await api('/api/orders/dryrun-demo/audit-count');
        document.getElementById('auditCountText').textContent = '当前审计计数：' + count.count;
    } catch (e) {
        document.getElementById('auditCountText').textContent = e.message;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('loadList').onclick = loadList;
    document.getElementById('addItem').onclick = addItemRow;
    document.getElementById('createOrder').onclick = createOrder;
    document.getElementById('getOrder').onclick = getOrder;
    document.getElementById('payOrder').onclick = payOrder;
    document.getElementById('cancelOrder').onclick = cancelOrder;
    document.getElementById('deleteOrder').onclick = deleteOrder;
    document.getElementById('runDryRun').onclick = runDryRun;
    document.getElementById('loadAuditCount').onclick = loadAuditCount;
    addItemRow();
    loadList();
    loadAuditCount();
});
