'use strict';

document.addEventListener("DOMContentLoaded", function(event) {
    const ws_url = document.getElementById("ws_identifier").dataset.wsUrl
    const ws = new WebSocket(ws_url)
    const peerConnection = new RTCPeerConnection();

    let message = { data: "regular message" };
    let msg_send = 0;
    let msg_received = 0;

    const dataChannel = peerConnection.createDataChannel("channel",
        { ordered:false});

    // Websocket handlers

    $(document).on('submit', '#ws-message-form', function(e) {
        e.preventDefault();
        message = e.target[0].value
    });

    ws.onopen = function(event) {
        console.log("ws.onopen");
        peerConnection.createOffer()
            .then(function(desc) {
                return peerConnection.setLocalDescription(desc);
            }).then( function() {
                console.log("Created offer, icecandidates will be sent");
            }).catch(
                function(error) {console.log("Offer Error" + error)}
            );
    }

    ws.onmessage = function(event) {
        console.log("ws.onmessage");
        const data = JSON.parse(event.data)
        if(data.type === "offer") {
            const sd = new RTCSessionDescription({type: "answer", sdp: data.answer});
            console.log(data.answer)
            peerConnection.setRemoteDescription(sd).then(function (sess) {
                console.log("Set remote with success ");
            }).catch(function(e){
                console.log("error setting remote: "+e);
            });
        }
    //    console.log(data.time_send)
    //    console.log(data.time_received)
    //    console.log("Time it took: " + (data.time_received - data.time_send))
    }

    ws.onclose = function(event) {
        console.log("ws.onclose");
    }

    ws.onerror = function(event) {
        console.log("ws.onerror");
        console.log(event)
    }


    // PeerConnection handlers

    peerConnection.onicecandidate = function(e) {
        console.log('IceCand: ' + JSON.stringify(e));
        if (peerConnection.iceGatheringState === 'complete') {
            console.log("Candidate: " + JSON.stringify(e.candidate));
            console.log("IceState" + peerConnection.iceGatheringState);
            const offer = JSON.stringify(
                peerConnection.localDescription
            )
            ws.send(offer);
        }
    };


    peerConnection.onsignalingstatechange = function(event) {
        console.log("Signal: " + JSON.stringify(event));
    };

    peerConnection.onconnectionstatechange = function(event) {
      console.log("State: " + JSON.stringify(event));
      switch(peerConnection.connectionState) {
        case "connected":
          break;
        case "disconnected":
            break;
        case "failed":
          break;
        case "closed":
          break;
      }
    };

    peerConnection.ondatachannel = function (dt) {
        console.log("datachannel: " + JSON.stringify(ev))
    };

    peerConnection.onidpvalidationerror = function (dt) {
        console.log("ipvalidationfail: " + JSON.stringify(ev))
    };

    peerConnection.onclose = function (ev) {
        console.log("Close: " + JSON.stringify(ev))
    };

    peerConnection.iceconnectionstatechange = function(ev) {
        console.log("icestate: " + JSON.stringify(ev));
    };

    peerConnection.onicegatheringstatechange = function(ev) {
        console.log("gather: " + JSON.stringify(ev));
    };

    peerConnection.onidpvalidationerror = function(ev) {
        console.log("ipvalid: " + JSON.stringify(ev));
    };

    peerConnection.onnegotiationneeded = function(ev) {
        console.log("negneded: " + JSON.stringify(ev));
    };

    peerConnection.onpeeridentity = function(ev) {
        console.log("peerident: " + JSON.stringify(ev));
    };

    // DataChannel handlers.

    dataChannel.onerror = function (e) {
        console.log("Error: " + JSON.stringify(e));
        console.log("Got message: " + e.data);
    };

    dataChannel.onclose = function (e) {
        console.log("Close: " + JSON.stringify(e));
    };

    dataChannel.onopen = function (e) {
        console.log("Open data channel: " + JSON.stringify(e));
        setInterval(function() {
            dataChannel.send(JSON.stringify(message))
            msg_send += 1
            document.getElementById("dc_send").innerHTML = msg_send;
        }, 500)
        console.log("closing websocket")
        ws.close()
    };

    dataChannel.onmessage = function (e) {
        console.log("received message")
        msg_received += 1
        document.getElementById("dc_received").innerHTML = msg_received;
        console.log(e.data)
    }

    window.onbeforeunload = function(){
       dataChannel.close()
    }
    // OR
    window.addEventListener("beforeunload", function(e){
       dataChannel.close()
    }, false);
});