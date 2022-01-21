package io.github.vaqxai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class NetworkNode {

	public final String identifier;
	public boolean silentMode = false;
	private int lastRedirIndex = -1;

	// for client communication
	public TCPServer nodeTcpServer = null;

	// for inter-node communication
	public NodeUDPServer nodeUdpServer = null;

	ArrayList<String> innerAddresses = new ArrayList<>();
	ArrayList<String> outerAddresses = new ArrayList<>();

	HashMap<String, NetworkResource> resources = new HashMap<>();

	/**
	 * Key: ClientID:RequestID_SearchID/SearchSender Value: RemainingSearches UnfilledResource1:UnfilledResource1Amt ...
	 */
	HashMap<String, String> waitingSearches = new HashMap<>();

	/**
	 * Where String is order ID : clientIdentifier:sequentialNumber
	 */
	HashMap<String, ResourceRequest> requestsInProgress = new HashMap<>();

	private void printInfo(String info){

		if(!silentMode)
			System.out.println("[" + identifier + "] " + info);
	}

	private void processAddCmd(String addNodeAddrStr){

		String[] addNodeAddrStrArr = addNodeAddrStr.split(" ");
		
		for (String addNodeAddr : addNodeAddrStrArr){
			if(innerAddresses.size() < 4){
				innerAddresses.add(addNodeAddr);
				printInfo("  Added " + addNodeAddr + " to inner addresses.");
			} else if(outerAddresses.size() < 4){
				outerAddresses.add(addNodeAddr);
				printInfo("  Added " + addNodeAddr + " to outer addresses.");
			} else {
				throw new RuntimeException("Got add command despite having 8 networks conntected");
			}
		}

	}

	private String getOwnAddressString(){
		try{
			return Inet4Address.getLocalHost().getHostAddress() + ":" + nodeUdpServer.getSocket().getLocalPort();
		} catch (UnknownHostException e) {
			return Inet4Address.getLoopbackAddress().getHostAddress() + ":" + nodeUdpServer.getSocket().getLocalPort();
		}
	}

	private void processNewNode(ArrayList<String> addressTable, String newNodeAddrStr){

		// send new node info to existing nodes in own address tab
		for(String gatewayAddrStr : addressTable){
			String gatewayAddr = gatewayAddrStr.split(":")[0];
			int gatewayPort = Integer.parseInt(gatewayAddrStr.split(":")[1]);
			nodeUdpServer.send("ADD " + newNodeAddrStr, gatewayAddr, gatewayPort);
			printInfo("Sent ADD to " + gatewayAddrStr + " with address " + newNodeAddrStr);
		}

		// send other nodes' info to the new node
		String addStr = "";
		for(String gatewayAddrStr : addressTable){
			addStr += gatewayAddrStr + " ";
		}

		if(addStr != ""){
			String newNodeAddr = newNodeAddrStr.split(":")[0];
			int newNodePort = Integer.parseInt(newNodeAddrStr.split(":")[1]);
			nodeUdpServer.send("ADD " + addStr, newNodeAddr, newNodePort);
			printInfo("Sent ADD to " + newNodeAddrStr + " with addresses " + addStr);
		}

		// add new node to own address table
		addressTable.add(newNodeAddrStr);
		printInfo("  Processed new node " + newNodeAddrStr + " and added them to the network and own table.");

	}

	private void nodeConnected(String gatewayAddrStr){

		if(innerAddresses.size() < 4){ // Inner addresses not full

			processNewNode(innerAddresses, gatewayAddrStr);

		} else if(outerAddresses.size() < 4){ // Outer addresses not full

			processNewNode(outerAddresses, gatewayAddrStr);

		} else {
			
			if(lastRedirIndex > 2)
				lastRedirIndex = -1;
			
			nodeUdpServer.send(
				"DIR " + outerAddresses.get(++lastRedirIndex),
				gatewayAddrStr.split(":")[0],
				Integer.parseInt(gatewayAddrStr.split(":")[1])
			);

			printInfo("Redirected " + gatewayAddrStr + " to " + outerAddresses.get(lastRedirIndex) + " because the current network member limit has been reached.");

		}

	}

	private void processResources(String resourceString){

		String[] resourceArray = resourceString.split(" ");

		for(String singleResString : resourceArray){
			String resName = singleResString.split(":")[0];
			int resAmount = Integer.parseInt(singleResString.split(":")[1]);

			resources.put(resName, new NetworkResource(resName, getOwnAddressString(), resAmount));
		}

	}

	private void processLock(String commandArgs){

		String[] commandArray = commandArgs.split(" ");

		String senderAddress = commandArray[0];
		String originatorAddress = commandArray[1];
		String orderID = commandArray[2];


		String[] orderContents = Arrays.copyOfRange(commandArray, 3, commandArray.length);
		HashMap<String, Integer> orderParts = new HashMap<>();
		int totalResourcesNeeded = 0;

		for(String orderPart : orderContents){
			orderParts.put(orderPart.split(":")[0], Integer.parseInt(orderPart.split(":")[1]));
			totalResourcesNeeded += Integer.parseInt(orderPart.split(":")[1]);
		}

		HashMap<String, Integer> originalParts = new HashMap<>(orderParts);

		// how many resources can we lock?
		int lockedAmt = 0;
		for(Entry<String, NetworkResource> res : resources.entrySet()){
			if(orderParts.containsKey(res.getKey())){
				int tempLockedAmt = res.getValue().lock(orderParts.get(res.getKey()));
				if(tempLockedAmt > 0)
					orderParts.replace(res.getKey(), orderParts.get(res.getKey()) - tempLockedAmt);
				lockedAmt += tempLockedAmt;
			}
		}

		// partial request filled
		if(lockedAmt < totalResourcesNeeded && outerAddresses.size() > 0) {

			waitingSearches.put(orderID + "/" + senderAddress, String.valueOf(outerAddresses.size())); // waiting for n FINs to send to X

			String remainderReceiver = outerAddresses.get((int)Math.round(Math.random() * (outerAddresses.size() - 1)));

			// Forward LOKs
			
			for(Entry<String, Integer> res : orderParts.entrySet()){

				int amountRemaining = res.getValue() % outerAddresses.size();
				int requestAmount = (res.getValue() - amountRemaining ) / outerAddresses.size();
				
				for(String address : outerAddresses){
					int amountToAsk = requestAmount;
					if(address.equals(remainderReceiver)){
						amountToAsk += amountRemaining;
					}
					nodeUdpServer.send("LOK " + originatorAddress + " " + orderID + " " + res.getKey() + ":" + amountToAsk, address.split(":")[0], Integer.parseInt(address.split(":")[1]));

					printInfo("Forwarded LOK to " + address + " for " + res.getKey() + ":" + amountToAsk);
				}

			}

		}

		if (lockedAmt > 0){
			// LKR to originator
			String lkrMessage = "LKR " + originatorAddress + " " + orderID + " ";
			for(Entry<String, Integer> orderPart : orderParts.entrySet()){
				lkrMessage += orderPart.getKey() + ":" + (originalParts.get(orderPart.getKey()) - orderPart.getValue());
			}

			nodeUdpServer.send(lkrMessage, originatorAddress.split(":")[0], Integer.parseInt(originatorAddress.split(":")[1]));

			printInfo("Sent LKR to " + originatorAddress + " with the following contents: " + lkrMessage);
		}

		// full request filled
		if (lockedAmt == totalResourcesNeeded) {
			
			// no need to send remaining order contents if full request was filled
			nodeUdpServer.send("FIN " + originatorAddress + " " + orderID, senderAddress.split(":")[0], Integer.parseInt(senderAddress.split(":")[1]));

			printInfo("Sent FIN to " + senderAddress + " for order " + orderID + " because a request was fully filled");

		}else if (lockedAmt == 0) { // nothing filled, and can't forward. Search is finished, send back resources that couldn't be locked

			nodeUdpServer.send(
				"FIN " +
				originatorAddress +
				" " +
				orderID +
				" " +
				Arrays.stream(commandArray, 3, commandArray.length).collect(Collectors.joining(" ")),
				senderAddress.split(":")[0],
				Integer.parseInt(senderAddress.split(":")[1])
			);

			printInfo("Sent FIN to " + senderAddress + " for order " + orderID + " because a request could not be filled or forwarded");

		}


		// 1. Check our own resources for availability

		// 2. If we can't fully fill the request, but can fill it partially, lock our resource, send a response, and forward a partial request to our sub-net if we have it.

		// 3. If we can't fill the request at all, just forward a partial request to our sub-net if we have one.

		// Send a negative response if we can't fill the request at all and we don't have a subnet.

	}

	/**
	 * Executed internally when search for available values has ended (SUC or FIL)
	 * @param request forwarded request from SUC or FIL
	 * @param commandArgs forwarded command arguments from SUC or FIL
	 */
	private void requestSearchEnded(String commandArgs){

			// Command Args:
			// 0. message address
			// 1. request originator address
			// 2. requestID
			// 3-n. if the request was not filled, resources that were not allocated

			// Schema:
			// 1. Search outer addresses if we have them (twice - if the first search did not complete the request)
			// 2. Search inner addresses if they were not searched on the first search (and if they were - conduct a second search if the request was not fulfilled)
			// The second search omits addresses which are known to not have any of the requested resources

			String[] commandArray = commandArgs.split(" ");

			String requestID = commandArray[2]; // requestID format: ClientID:OrderID_SearchNo

			String responseAddress = commandArray[0];

			// are we the originator of this request?
			if (getOwnAddressString().equals(commandArray[1])){
				responseAddress = commandArray[1];
			}

			String searchID = requestID + "/" + responseAddress;
			int searchesLeft = Integer.parseInt(waitingSearches.get(searchID).split(" ")[0]);
			String resourcesNotFound = "";

			// Are there any resources left to be found
			if(commandArray.length > 3) {
				if(waitingSearches.get(searchID).split(" ").length > 1)
					resourcesNotFound = waitingSearches.get(searchID).split(" ")[1];
				else // Add resources not found to waiting searches
					waitingSearches.replace(
						searchID, waitingSearches.get(searchID) +
						" " +
						Arrays.stream(commandArray, 3, commandArray.length).collect(Collectors.joining(" "))
					);
				waitingSearches.replace(searchID, searchesLeft - 1 + " " + resourcesNotFound); // we are waiting for one less denial/success for our own request
			} else {
				waitingSearches.replace(searchID, searchesLeft - 1 + ""); // we are waiting for one less denial/success for our own request
			}
			searchesLeft -= 1;

			// This was the last response. Process the request further.
			if(searchesLeft == 0){

				boolean isFirstSearch = commandArray[2].split("_")[1].equals("1");

				// if it came from one of our outer addresses
				boolean isSearchFromOuter = outerAddresses.contains(commandArray[0]);
				boolean isSearchFromInner = innerAddresses.contains(commandArray[0]);

				// did it come from this PC? consider only ports then
				if(
					!(isSearchFromInner || isSearchFromOuter) && (
						commandArray[0].split(":")[0].equals(Inet4Address.getLoopbackAddress().getHostAddress().split(":")[0]) ||
						commandArray[0].split(":")[0].equals(getOwnAddressString().split(":")[0])
					)
				){
					String remotePort = commandArray[0].split(":")[1];

					for(String address : outerAddresses){
						System.out.println(address.split(":")[1] + " = " + remotePort);
						if (address.split(":")[1].equals(remotePort)) { isSearchFromOuter = true; break; }
					}

					for(String address : innerAddresses){
						System.out.println(address.split(":")[1] + " = " + remotePort);
						if (address.split(":")[1].equals(remotePort)) { isSearchFromInner = true; break; }
					}
				}

				HashMap<String, Integer> remainingResources = new HashMap<>();
				if(!resourcesNotFound.equals(""))
					for(String resourceString : resourcesNotFound.split(" ")){
						remainingResources.put(resourceString.split(":")[0], Integer.parseInt(resourceString.split(":")[1]));
					}

				// This was the first search and it was on outer addresses, conduct second search on outer addresses
				if(isFirstSearch && isSearchFromOuter && !resourcesNotFound.equals("")) {
					processLockSend(commandArray[1], requestID.split("_")[0] + "_2", remainingResources, outerAddresses);
					System.out.println("outer second");
				// This was a second search on outer addresses, conduct first search on inner addresses
				} else if(!isFirstSearch && isSearchFromOuter && !resourcesNotFound.equals("")) {
					processLockSend(commandArray[1], requestID.split("_")[0] + "_1", remainingResources, innerAddresses);
					System.out.println("inner first");
				// This was the first search on inner addresses (whether we had outer addresses or not), conduct second search on inner addresses
				} else if (isFirstSearch && isSearchFromInner && !resourcesNotFound.equals("")) {
					processLockSend(commandArray[1], requestID.split("_")[0] + "_2", remainingResources, innerAddresses);
					System.out.println("inner second");
				}
				// This was the second search on inner addresses (final search)
				else {

					if(getOwnAddressString().equals(commandArray[1])){
						System.out.println("Request for which we are an originator was finished");
						// All resources were locked
						ResourceRequest rrq = requestsInProgress.get(requestID.split("_")[0]);

						if(resourcesNotFound.equals("")){
							rrq.finalizeOrder(nodeUdpServer);
						} else {
							rrq.release(nodeUdpServer);
						}
					} else {
						System.out.println("Request for which we are not an originator was finished");

						String message = "FIN " + commandArray[1] + " " + commandArray[2];

						if(!resourcesNotFound.equals("")){
							message += (" " + resourcesNotFound);
						}

						nodeUdpServer.send(message, commandArray[1].split(":")[0], Integer.parseInt(commandArray[1].split(":")[1]));
					}
				}

			}
	}

	private void processRedirect(String commandArgs){

			innerAddresses.remove(0);

			String gatewayAddr = commandArgs.split(":")[0];
			int gatewayPort = Integer.parseInt(commandArgs.split(":")[1]);

			innerAddresses.add(commandArgs);
			nodeUdpServer.send("CON", gatewayAddr, gatewayPort);
			printInfo("  Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

	}

	private void processLockResponse(String commandArgs){

		String[] commandArray = commandArgs.split(" ");

		// Command Args:
		// 0. message sender address
		// 1. request originator address
		// 2. requestID (clientID:orderID_searchNo)
		// 3-n. either the resourcs that were successfully locked (SUC) or resources that failed to be locked (FIL)

		ResourceRequest rrq = requestsInProgress.get(commandArray[2].split("_")[0]);
		for(int i = 3; i < commandArray.length; i++){
			String resourceIdentifier = commandArray[i].split(":")[0];
			int resourceAmount = Integer.parseInt(commandArray[i].split(":")[1]);

			rrq.addLockedResource(commandArray[0], resourceIdentifier, resourceAmount);
			printInfo(resourceAmount + " of resource " + resourceIdentifier + " was locked on " + commandArray[0] + " for request ID: " + rrq.getIdentifier());
		}

	}

	private void processUnlock(String commandArgs){
		String[] commandArray = commandArgs.split(" ");

		for(String resourceString : commandArray){
			String resourceIdentifier = resourceString.split(":")[0];
			int resourceAmount = Integer.parseInt(resourceString.split(":")[1]);

			this.resources.get(resourceIdentifier).unlock(resourceAmount);
		}

		printInfo("Someone unlocked the following resources on this node: " + commandArgs);
	}

	private void processReservation(String argsStr){

		String[] args = argsStr.split(" ");

		for(int i = 2; i < args.length; i++){

			String resourceIdentifier = args[i].split(":")[0];
			int resourceAmount = Integer.parseInt(args[i].split(":")[1]);

			resources.get(resourceIdentifier).reserve(args[1], resourceAmount);
		}
		
	}

	private void processRequestFail(ResourceRequest request){
		printInfo("Order " + request.getIdentifier() + " placed with this node could not be completed and has failed.");

		request.getClientHandler().send("FAILED");
		request.release(nodeUdpServer);
		request.status = RequestStatus.DENIED;
	}

	/**
	 * We are the originator and we are sending a first search or inner or outer adresses
	 * @param request the ResourceRequest we are sending this for
	 * @param addresses the addresses we are sending this to
	 */
	public void processLockSend(ResourceRequest request, ArrayList<String> addresses){
		processLockSend(getOwnAddressString(), String.valueOf(request.getIdentifier()) + "_1", request.getOrderRemaining(), addresses);
	}

	public void processLockSend(String originator, String orderId, HashMap<String, Integer> requestedResources, ArrayList<String> addresses){

		for (Entry<String, Integer> orderPart : requestedResources.entrySet()){

			int amountRemaining = requestedResources.get(orderPart.getKey());

			int requestAmount = 0;
			String remainderReceiver = "";

			if (addresses.size() > 0){

				requestAmount = (amountRemaining - (amountRemaining % addresses.size()) ) / addresses.size();
				amountRemaining = amountRemaining % addresses.size();

				remainderReceiver = addresses.get((int)Math.round(Math.random() * (addresses.size() - 1)));

			} else {

				// Are you trying to send a lock to zero addresses?

			}

			waitingSearches.put(orderId + "/" + originator, String.valueOf(addresses.size())); // waiting for n FINs to send to X

			for(String address : addresses){
				
				int amountToRequest = requestAmount;
				if(address.equals(remainderReceiver)) amountToRequest += amountRemaining;

				nodeUdpServer.send(
					"LOK " + getOwnAddressString() + " " + orderId + " " + orderPart.getKey() + ":" + amountToRequest,
					address.split(":")[0],
					Integer.parseInt(address.split(":")[1])
				);

			}

			printInfo("Failed to allocate needed resources on the local node. Forwarding the request to the subnet.");
			
		}

	}

	public void printResources(){

		printInfo("Resources:");
		for(Entry<String, NetworkResource> res : resources.entrySet()){
			NetworkResource resVal = res.getValue();
			printInfo("  Name: " + resVal.getIdentifier());
			printInfo("    Available: " + resVal.getAvailable());
			printInfo("    Locked: " + resVal.getLocked());
			printInfo("    Allocated: " + resVal.getOwners().toString());
		}

	}

	public NetworkNode(String identifier, int tcpPort, String gatewayAddr, int gatewayPort, String resources){

		this.identifier = identifier + " (:" + tcpPort + ")";
	
		printInfo("Initializing...");

		ExecutorService execSvc = Executors.newFixedThreadPool(2);

		String[] messageText = new String[3];
		boolean[] messageFromNode = new boolean[1];

		final ReentrantLock lock = new ReentrantLock();
		final Condition messageReceived = lock.newCondition();
		final Condition messageProcessed = lock.newCondition();

		nodeUdpServer = new NodeUDPServer(tcpPort);
		nodeTcpServer = new TCPServer(tcpPort);

		execSvc.submit(nodeUdpServer);
		execSvc.submit(nodeTcpServer);

		nodeTcpServer.setSilentMode(true);
		nodeUdpServer.setSilentMode(true);

		// Client message callback

		nodeTcpServer.setAutoResponse((sock, message) -> {
			try{
				lock.lock();
				messageText[0] = message;
				messageText[1] = "" + sock.getInetAddress();
				messageText[2] = "" + sock.getPort();
				messageFromNode[0] = false;
				messageReceived.signalAll();
				messageProcessed.await();
				lock.unlock();
			} catch (InterruptedException e){
				System.err.println(e);
			}
			return "";
		});

		// Node message callback

		nodeUdpServer.setCallback((packet) -> {

			try{
			lock.lock();
			InetAddress incomingAdddress = packet.getAddress();

			String receivedStr = new String(packet.getData(), 0, packet.getLength());

			messageText[0] = receivedStr;
			messageText[1] = incomingAdddress.getHostAddress();
			messageText[2] = "" + packet.getPort();
			messageFromNode[0] = true;

			messageReceived.signalAll();
			//messageProcessed.await();
			lock.unlock();

			} catch (Exception e){
				System.err.println(e);
				lock.unlock();
			}

		});

		processResources(resources);

		// Network handshake for non-first nodes

		if((gatewayAddr != null) && (gatewayPort > 0)){

			innerAddresses.add(gatewayAddr + ":" + gatewayPort);
			nodeUdpServer.send("HONK", gatewayAddr, gatewayPort);
			printInfo("  Asked (honked at) " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

		}

		// Main loop

		printInfo("Started!");

		while(true){
			lock.lock();
			try{

				// Ignore empty/improperly structured messages

				if(messageText[0] == null || messageText[1] == null || messageText[2] == null){
					continue;
				}

				printInfo("INCOMING MSG: [" + messageText[1] + ":" + messageText[2] + "] > " + messageText[0]);

				// Control messages

				if(messageText[0].equals("TERMINATE")){
					// Propagate termination, like a virus.
					for(String addrStr : outerAddresses){
						nodeUdpServer.send("TERMINATE", addrStr.split(":")[0], Integer.parseInt(addrStr.split(":")[1]));
					}
					for(String addrStr : innerAddresses){
						nodeUdpServer.send("TERMINATE", addrStr.split(":")[0], Integer.parseInt(addrStr.split(":")[1]));
					}
					break;
				};

				// Debug/Test commands

				if(messageText[0].equals("RESOURCES")) printResources();

				if(messageText[0].startsWith("LOCK")){
					String res = messageText[0].split(" ")[1];
					this.resources.get(res.split(":")[0]).lock(Integer.parseInt(res.split(":")[1]));
				}

				// Regular messages/inter-node/client communications

				// Parse the message into a command and arguments, to be executed

				String command = "";
				String commandArgs = "";
				if(messageText[0].length() > 3){
					command = messageText[0].substring(0, 3);
					commandArgs = messageText[0].substring(4);
				} else if (messageText[0].length() == 3) {
					command = messageText[0];
				}

				// Inter-node communications

				if(messageFromNode[0]){

					switch(command){
						case "HON"://K // New node connects to the network (honks to us) via us
							nodeConnected(messageText[1] + ":" + messageText[2]);
							break;
						case "ADD": // We are adding a new node to our tables
							processAddCmd(commandArgs);
							break;
						case "DIR": // We are being redirected
							processRedirect(commandArgs);
							break;
						case "LOK": // Someone requests our resources
							processLock(messageText[1] + ":" + messageText[2] + " " + commandArgs);
							break;
						case "LKR": // Someone responded to one of our locks
							processLockResponse(messageText[1] + ":" + messageText[2] + " " + commandArgs);
							break;
						case "FIN": // Someone ended their search
							requestSearchEnded(messageText[1] + ":" + messageText[2] + " " + commandArgs);
							break;
						case "ULK": // Someone wants to unlock our resources
							processUnlock(commandArgs);
							break;
						case "RES": // Someone wants to reserve our resources
							processReservation(messageText[1] + ":" + messageText[2] + " " + commandArgs);
							break;
					}

				}

				// Client-node communication, only communication to be received here, is a reservation request.

				else if (messageText[0].split(" ").length > 1) {

					String[] clientMessageArray = messageText[0].split(" ");

					ClientHandler cli = null;

					for(ClientHandler client : nodeTcpServer.getAllClients()){
						Socket sock = client.getSocket();
						if(String.valueOf(sock.getInetAddress()).equals(messageText[1]) &&
						String.valueOf(sock.getPort()).equals(messageText[2])){
							cli = client;
							break;
						}
					}

					String clientIdentifier = clientMessageArray[0];
					String[] requestArray = Arrays.copyOfRange(clientMessageArray, 1, clientMessageArray.length);
					ResourceRequest request = new ResourceRequest(clientIdentifier, cli, requestArray);
					request.status = RequestStatus.PROCESSING;

					// 1. Check if we have the resources they want ourselves, and respond immediately if we do, block all we can if we don't.

					int[] amountLocked = {0};

					this.resources.values().stream().filter(res -> {
						return request.getOrder().keySet().contains(res.getIdentifier());
					}).forEach(res -> {
						int tempLockedAmt = res.lock(request.getOrder().get(res.getIdentifier()));
						printInfo(tempLockedAmt + " of resource " + res.getIdentifier() + " was locked on the local node for request ID: " + request.getIdentifier());
						amountLocked[0] += tempLockedAmt;
						request.addLockedResource(getOwnAddressString(), res.getIdentifier(), tempLockedAmt);
					});

					if(amountLocked[0] == request.order.values().stream().reduce(0, (a, b) -> a + b)){

						request.status = RequestStatus.FINALIZED;
						request.finalizeOrder(nodeUdpServer); // this will just send us reservations
						printInfo("Order " + request.getIdentifier() + " placed with this node was successfully completed and allocated.");

					} else {
						if(outerAddresses.size() == 0 && innerAddresses.size() > 0){
							requestsInProgress.put(request.getIdentifier(), request);
							processLockSend(request, innerAddresses);
						} else if (outerAddresses.size() > 0) {
							requestsInProgress.put(request.getIdentifier(), request);
							processLockSend(request, outerAddresses);
						} else {
							processRequestFail(request);
						}
					}



				}

				messageText[0] = "ERROR";

				messageProcessed.signalAll();

				messageReceived.await();

			} catch (InterruptedException e) {
				System.err.println(e);
				break;
			} finally {
				lock.unlock();
			}
		}

		printInfo("END");

	}

	public static void main(String[] args) throws IOException {

	// parameter storage
	String gatewayAddr = null;
	int gatewayPort = 0;
	String identifier = null;
	String resources = null;
	int tcpPort = 0;

	// Parameter scan loop
	for(int i=0; i<args.length; i++) {
			switch (args[i]) {
				case "-ident":
					identifier = args[++i];
					break;
				case "-gateway":
					String[] gatewayArray = args[++i].split(":");
					gatewayAddr = gatewayArray[0];
					gatewayPort = Integer.parseInt(gatewayArray[1]);
					break;
				case "-tcpport":
					tcpPort = Integer.parseInt(args[++i]);
				break;
				default:
					if(resources == null) resources = args[i];
					else resources += " " + args[i];
			}
	}

	new NetworkNode(identifier, tcpPort, gatewayAddr, gatewayPort, resources);

	}
}
