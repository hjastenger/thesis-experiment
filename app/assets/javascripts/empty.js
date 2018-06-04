'use strict';

const ws = new WebSocket("ws://localhost:9000/websocket_ping");

ws.onopen = function(event) {
    console.log("ws.onopen");
    ws.send(JSON.stringify({ data: "working" }))
}

ws.onmessage = function(event) {
    console.log("ws.onmessage");
    const data = JSON.parse(event.data)
    console.log(data)
}

ws.onclose = function(event) {
    console.log("ws.onclose");
}

ws.onerror = function(event) {
    console.log("ws.onerror");
    console.log(event)
}