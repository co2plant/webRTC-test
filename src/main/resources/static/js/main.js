var ws = new WebSocket('ws://' + location.host + '/signal');
var videoInput;
var videoOutput;
var webRtcPeer;
var startBtn;
var stopBtn;

const I_CAN_START = 0;
const I_AM_STARTING = 1;
const I_CAN_STOP = 2;
var state = I_CAN_START;

window.onload = function () {
	startBtn = document.getElementById('startBtn');
	stopBtn = document.getElementById('stopBtn');
	videoInput = document.getElementById('videoInput');
	videoOutput = document.getElementById('videoOutput');

	startBtn.addEventListener('click', start);
	stopBtn.addEventListener('click', stop);
}

ws.onmessage = function (message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
		case 'startResponse':
			startResponse(parsedMessage);
			break;
		case 'error':
			if (state == I_AM_STARTING) {
				setState(I_CAN_START);
			}
			onError('Error message from server: ' + parsedMessage.message);
			break;
		case 'iceCandidate':
			webRtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate: " + error);
					return;
				}
			});
			break;
		default:
			if (state == I_AM_STARTING) {
				setState(I_CAN_START);
			}
			onError('Unrecognized message', parsedMessage);
	}
}

function start() {
	console.log('Starting video call ...');

	// Disable start button
	setState(I_AM_STARTING);
	showSpinner(videoInput, videoOutput);

	// Modern 'getDisplayMedia' for screen sharing
	if (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) {
		navigator.mediaDevices.getDisplayMedia({ video: true })
			.then(stream => {
				// Determine video source (local stream)
				videoInput.srcObject = stream;

				// Create WebRtcPeer with the captured stream
				var options = {
					localVideo: videoInput,
					remoteVideo: videoOutput,
					onicecandidate: onIceCandidate,
					videoStream: stream, // Pass the stream explicitly
					mediaConstraints: { // Constraints are mandatory even if stream is provided in some versions
						audio: false,
						video: true
					}
				}

				webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
					function (error) {
						if (error) {
							return console.error(error);
						}
						webRtcPeer.generateOffer(onOffer);
					});
			})
			.catch(error => {
				console.error("Error accessing screen:", error);
				setState(I_CAN_START);
				hideSpinner();
			});
	} else {
		alert("Your browser does not support getDisplayMedia!");
		setState(I_CAN_START);
	}
}

function onOffer(error, offerSdp) {
	if (error) return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);
	var message = {
		id: 'start',
		sdpOffer: offerSdp
	}
	sendMessage(message);
}

function onError(error) {
	console.error(error);
}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate));

	var message = {
		id: 'onIceCandidate',
		candidate: candidate
	}
	sendMessage(message);
}

function startResponse(message) {
	setState(I_CAN_STOP);
	console.log('SDP answer received from server. Processing ...');

	webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
		if (error) return console.error(error);
	});
}

function stop() {
	console.log('Stopping video call ...');
	setState(I_CAN_START);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;

		var message = {
			id: 'stop'
		}
		sendMessage(message);
	}
	hideSpinner(videoInput, videoOutput);
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
}


function setState(nextState) {
	state = nextState;
	switch (nextState) {
		case I_CAN_START:
			startBtn.disabled = false;
			stopBtn.disabled = true;
			break;

		case I_AM_STARTING:
			startBtn.disabled = true;
			stopBtn.disabled = true;
			break;

		case I_CAN_STOP:
			startBtn.disabled = true;
			stopBtn.disabled = false;
			break;

		default:
			onError('Unknown state ' + nextState);
			return;
	}
}

function showSpinner() {
	// Placeholder
}

function hideSpinner() {
	// Placeholder
}
