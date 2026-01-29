var ws = new WebSocket('ws://' + location.host + '/signal');
var participants = {}; // k: userName, v: participant (WebRtcPeer + videoEl)
var name; // My Name
var myRole; // "user" or "manager"

window.onbeforeunload = function () {
	ws.close();
};

ws.onmessage = function (message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
		case 'existingParticipants':
			onExistingParticipants(parsedMessage);
			break;
		case 'newParticipantArrived':
			onNewParticipant(parsedMessage);
			break;
		case 'participantLeft':
			onParticipantLeft(parsedMessage);
			break;
		case 'receiveVideoAnswer':
			receiveVideoResponse(parsedMessage);
			break;
		case 'iceCandidate':
			// parsedMessage.candidate = the IceCandidate
			// parsedMessage.name = who's connection this belongs to (could be me, or a remote user)
			participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate: " + error);
					return;
				}
			});
			break;
		default:
			console.error('Unrecognized message', parsedMessage);
	}
}

document.getElementById('joinBtn').addEventListener('click', function () {
	name = document.getElementById('name').value;
	var room = document.getElementById('roomName').value;
	// Get selected role
	var roleRadios = document.getElementsByName('role');
	for (var i = 0; i < roleRadios.length; i++) {
		if (roleRadios[i].checked) {
			myRole = roleRadios[i].value;
			break;
		}
	}

	if (!name || !room) {
		alert("Please enter both name and room!");
		return;
	}

	document.getElementById('room-header').innerText = room + " (" + myRole + ")";
	document.getElementById('join-section').classList.add('d-none');
	document.getElementById('room-section').classList.remove('d-none');

	// If Manager, hide "local video" box entirely since they don't share
	if (myRole === 'manager') {
		document.getElementById('local-video-wrapper').style.display = 'none';
	}

	var message = {
		id: 'joinRoom',
		name: name,
		room: room,
		role: myRole
	}
	sendMessage(message);
});

document.getElementById('leaveBtn').addEventListener('click', function () {
	sendMessage({ id: 'leaveRoom' });
	for (var key in participants) {
		participants[key].dispose();
	}
	participants = {};
	location.reload();
});

function onExistingParticipants(msg) {
	var constraints = {
		audio: false,
		video: true
	};

	// 1. My Logic (Publishing)
	if (myRole === 'user') {
		// I am a User, I MUST share screen
		var participant = new Participant(name);
		participants[name] = participant;
		var localVideo = document.getElementById('videoInput');

		if (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) {
			navigator.mediaDevices.getDisplayMedia({ video: true })
				.then(stream => {
					localVideo.srcObject = stream;

					var options = {
						localVideo: localVideo,
						onicecandidate: participant.onIceCandidate.bind(participant),
						videoStream: stream,
						mediaConstraints: constraints
					};

					participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
						function (error) {
							if (error) return console.error(error);
							this.generateOffer(participant.offerToReceiveVideo.bind(participant));
						});

					// After my stream starts, subscribe to others
					subscribeToOthers(msg.data);
				})
				.catch(error => {
					console.error("Error accessing screen:", error);
					alert("Users MUST share screen to join!");
					location.reload();
				});
		} else {
			alert("Your browser does not support getDisplayMedia!");
		}
	} else {
		// I am a Manager, I do NOT share screen. Just subscribe to others.
		subscribeToOthers(msg.data);
	}
}

function subscribeToOthers(participantsData) {
	// participantsData is now an array of {name, role} objects
	participantsData.forEach(function (userData) {
		if (userData.role === 'user') {
			receiveVideo(userData.name);
		} else {
			console.log("Skipping Manager (no video): " + userData.name);
		}
	});
}

function receiveVideo(sender) {
	// Create element for remote video
	var participant = new Participant(sender);
	participants[sender] = participant;

	var options = {
		remoteVideo: participant.getVideoElement(),
		onicecandidate: participant.onIceCandidate.bind(participant)
	}

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
		function (error) {
			if (error) return console.error(error);
			this.generateOffer(participant.offerToReceiveVideo.bind(participant));
		});
}

function onNewParticipant(request) {
	// request.name, request.role
	if (request.role === 'user') {
		receiveVideo(request.name);
	} else {
		console.log("New participant is Manager (no video): " + request.name);
		// Optionally display a toast "Manager [Name] joined"
	}
}

function receiveVideoResponse(result) {
	console.log("Received SDP Answer for " + result.name + ": " + result.sdpAnswer);
	participants[result.name].rtcPeer.processAnswer(result.sdpAnswer, function (error) {
		if (error) return console.error(error);
	});
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];
	participant.dispose();
	delete participants[request.name];
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	ws.send(jsonMessage);
}

// --- Participant Class ---
// Manages the Video Element and WebRtcPeer for a specific user
function Participant(name) {
	this.name = name;

	var container = document.createElement('div');
	container.className = 'col-md-4 video-box';
	container.id = name;

	// If it's NOT me, create a video tag
	// If it IS me, we used the existing video tag, so we don't need to append anything?
	// But to keep it uniform, let's treat everyone as having a 'container'.
	// Exception: 'name' is the global var 'name' (Me).

	var video;

	if (name === window.name) { // Wait, global 'name' variable
		// Self
		this.video = document.getElementById('videoInput');
	} else {
		// Remote
		var span = document.createElement('div');
		span.className = 'video-label';
		span.innerText = name;
		container.appendChild(span);

		video = document.createElement('video');
		video.id = 'video-' + name;
		video.autoplay = true;
		video.controls = false;
		video.playsInline = true;
		container.appendChild(video);

		document.getElementById('video-grid').appendChild(container);
		this.video = video;
	}

	var rtcPeer;

	this.getVideoElement = function () {
		return this.video;
	}

	this.offerToReceiveVideo = function (error, offerSdp, wp) {
		if (error) return console.error("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg = {
			id: "receiveVideoFrom",
			sender: name,
			sdpOffer: offerSdp
		};
		sendMessage(msg);
	}


	this.onIceCandidate = function (candidate, wp) {
		console.log("Sending ICE candidate for " + name + ": " + JSON.stringify(candidate));
		var message = {
			id: 'onIceCandidate',
			candidate: candidate,
			name: name // Send the name so server knows who this candidate belongs to
		};
		sendMessage(message);
	}

	this.dispose = function () {
		console.log('Disposing participant ' + this.name);
		this.rtcPeer.dispose();
		if (this.name !== window.name) {
			container.parentNode.removeChild(container);
		}
	}
}
