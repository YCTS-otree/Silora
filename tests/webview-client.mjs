export async function connectSilora() {
    const port = Number(process.env.SILORA_CDP_PORT || 9222);
    const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    const page = pages.find(p => p.url === 'https://appassets.androidplatform.net/assets/index.html' && p.title.includes('Silora'));
    if (!page) throw Error('Open Silora and forward its WebView debug socket first');
    const socket = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise(resolve => socket.onopen = resolve);
    let sequence = 0;
    const pending = new Map();
    socket.onmessage = event => {
        const result = JSON.parse(event.data);
        if (result.id) { pending.get(result.id)?.(result); pending.delete(result.id); }
    };
    async function evaluate(expression) {
        const result = await new Promise(resolve => {
            const id = ++sequence;
            pending.set(id, resolve);
            socket.send(JSON.stringify({ id, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
        });
        if (result.result.exceptionDetails) throw Error(JSON.stringify(result.result.exceptionDetails));
        return result.result.result.value;
    }
    for (let attempt = 0; attempt < 50; attempt++) {
        if (await evaluate("typeof native === 'function' && typeof state !== 'undefined' && state !== null"))
            return { evaluate, native: (action, data = {}) => evaluate(`native(${JSON.stringify(action)},${JSON.stringify(data)})`), close: () => socket.close() };
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    socket.close(); throw Error('Silora did not finish loading native state');
}
