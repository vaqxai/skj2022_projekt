package io.github.vaqxai;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.AbstractMap.SimpleEntry;
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
	 * An indexed map of all searches this node is conducting.
	 */
	HashMap<String, NetworkSearch> searches = new HashMap<>();

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

			this.searches.put(orderID, new NetworkSearch(orderID, outerAddresses.size(), originalParts));

			String remainderReceiver = outerAddresses.get((int)Math.round(Math.random() * (outerAddresses.size() - 1)));

			// Forward LOKs

			HashMap<String, String> partitionedResources = new HashMap<>();
			
			for(Entry<String, Integer> res : orderParts.entrySet()){

				int amountRemaining = res.getValue() % outerAddresses.size();
				int requestAmount = (res.getValue() - amountRemaining ) / outerAddresses.size();
				
				for(String address : outerAddresses){
					int amountToAsk = requestAmount;
					if(address.equals(remainderReceiver)){
						amountToAsk += amountRemaining;
					}

					if(amountToAsk == 0) continue;

					if(!partitionedResources.containsKey(address))
						partitionedResources.put(address, "");
					
					partitionedResources.replace(address, partitionedResources.get(address) + " " + res.getKey() + ":" + amountToAsk);

				}

			}

			for(Entry<String, String> res : partitionedResources.entrySet()){

				nodeUdpServer.send("LOK " + originatorAddress + " " + orderID + res.getValue(), res.getKey().split(":")[0], Integer.parseInt(res.getKey().split(":")[1]));

				printInfo("Forwarded LOK to " + res.getKey() + " for order " + orderID + " for resources" + res.getValue());

			}

		}

		if (lockedAmt > 0){
			// LKR to originator
			String lkrMessage = "LKR " + originatorAddress + " " + orderID + " ";
			for(Entry<String, Integer> orderPart : orderParts.entrySet()){
				lkrMessage += orderPart.getKey() + ":" + (originalParts.get(orderPart.getKey()) - orderPart.getValue()) + " ";
			}

			nodeUdpServer.send(lkrMessage, originatorAddress.split(":")[0], Integer.parseInt(originatorAddress.split(":")[1]));

			printInfo("Sent LKR to " + originatorAddress + " with the following contents: " + lkrMessage);
		}

		// full request filled
		if (lockedAmt == totalResourcesNeeded) {
			
			// no need to send remaining order contents if full request was filled
			nodeUdpServer.send("FIN " + originatorAddress + " " + orderID, senderAddress.split(":")[0], Integer.parseInt(senderAddress.split(":")[1]));

			printInfo("Sent FIN to " + senderAddress + " for order " + orderID + " because a request was fully filled");

			searches.remove(orderID);

		// nothing filled, or partially filled and can't forward. Search is finished, send back resources that couldn't be locked
		} else if (searches.size() == 0 || searches.get(orderID).getAmountLeft() == 0) {

			String remainingResources = searches.get(orderID).getRemainingString();

			nodeUdpServer.send(
				"FIN " +
				originatorAddress +
				" " +
				orderID +
				remainingResources,
				senderAddress.split(":")[0],
				Integer.parseInt(senderAddress.split(":")[1])
			);

			searches.remove(orderID);

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

			String searchID = commandArray[2]; // requestID format: ClientID:OrderID_SearchNo

			NetworkSearch s = searches.get(searchID);

			s.incAmountLeft(-1);

			printInfo("Searches remaining for order " + searchID + ": " + s.getAmountLeft());

			// This was the last response. Process the request further.
			if(s.getAmountLeft() == 0){

				// if it came from one of our outer addresses
				boolean isSearchFromOuter = outerAddresses.contains(commandArray[0]);
				boolean isSearchFromInner = innerAddresses.contains(commandArray[0]);

				boolean iAmTheOriginator = getOwnAddressString().equals(commandArray[1]);

				// did it come from this PC? consider only ports then
				if(
					!(isSearchFromInner || isSearchFromOuter) && (
						commandArray[0].split(":")[0].equals(Inet4Address.getLoopbackAddress().getHostAddress().split(":")[0]) ||
						commandArray[0].split(":")[0].equals(getOwnAddressString().split(":")[0])
					)
				){
					String remotePort = commandArray[0].split(":")[1];

					for(String address : outerAddresses){
						if (address.split(":")[1].equals(remotePort)) { isSearchFromOuter = true; break; }
					}

					for(String address : innerAddresses){
						if (address.split(":")[1].equals(remotePort)) { isSearchFromInner = true; break; }
					}
				}

				if(commandArray.length > 3)
					for(String resourceString : Arrays.copyOfRange(commandArray, 3, commandArray.length)){

						String resId = resourceString.split(":")[0];
						int resAmt = Integer.parseInt(resourceString.split(":")[1]);

						this.searches.get(searchID).modifyRemainingEntry(resId, resAmt);

						if(s.getResourceLeft(resId) == s.getResourceTotal(resId))
							s.addFailedHost(commandArray[0]);

					}

				ArrayList<String> addresses = null;

				if(isSearchFromOuter && !iAmTheOriginator){
					addresses = new ArrayList<String>(outerAddresses);
				} else {
					addresses = new ArrayList<String>(innerAddresses);
				}

				addresses = (ArrayList<String>) addresses.stream().filter(x -> !s.isFailedHost(x)).collect(Collectors.toList());

				printInfo(""+isSearchFromOuter);
				printInfo(""+isSearchFromInner);
				printInfo(""+s.getRemainingString());

				// This was the first search and it was on outer addresses, conduct second search on outer addresses
					if(s.isFirstSearch() && s.getRemaining().size() > 0 && addresses.size() > 0) {
						processLockSend(commandArray[1], searchID, s.getRemaining(), addresses);
						printInfo("Conducting second search on outer addresses: " + addresses.toString());
						s.makeFirst(); // next search will be first
					// This was a second search on outer addresses, conduct first search on inner addresses
					} else if(!s.isFirstSearch() && isSearchFromOuter && iAmTheOriginator && s.getRemaining().size() > 0 && addresses.size() > 0) {
						processLockSend(commandArray[1], searchID, s.getRemaining(), addresses);
						printInfo("Conducting first search on inner addresses: " + addresses.toString());
						s.makeSecond(); // next search will be second
					// This was the first search on inner addresses (whether we had outer addresses or not), conduct second search on inner addresses
					} else if (isSearchFromInner && iAmTheOriginator && s.getRemaining().size() > 0 && addresses.size() > 0) {
						processLockSend(commandArray[1], searchID, s.getRemaining(), addresses);
						printInfo("Conducting second search on inner addresses: " + addresses.toString());
					}
				// This was the second search on inner addresses (final search)
				else {

					if(iAmTheOriginator){
						printInfo("Request for which we are an originator was finished");
						// All resources were locked
						ResourceRequest rrq = requestsInProgress.get(searchID);

						if(rrq.remaining.values().stream().reduce(0, (a, b) -> a + b) == 0){
							rrq.finalizeOrder(nodeUdpServer);
						} else {
							processRequestFail(rrq);
						}

					} else {
						printInfo("Request for which we are not an originator was finished");

						String message = "FIN " + commandArray[1] + " " + commandArray[2];

						if(s.getRemainingString() != " ")
							message += s.getRemainingString();

						nodeUdpServer.send(message, commandArray[1].split(":")[0], Integer.parseInt(commandArray[1].split(":")[1]));
						printInfo("Sending FIN with the following data: " + message);

					}

					searches.remove(searchID);
				}

			}

			printInfo("Got FIN, searches: " + searches);
	}

	private void processRedirect(String commandArgs){

			innerAddresses.remove(0);

			String gatewayAddr = commandArgs.split(":")[0];
			int gatewayPort = Integer.parseInt(commandArgs.split(":")[1]);

			innerAddresses.add(commandArgs);
			nodeUdpServer.send("HONK", gatewayAddr, gatewayPort);
			printInfo("  Asked " + gatewayAddr + ":" + gatewayPort + " to connect us to the network.");

	}

	private void processLockResponse(String commandArgs){

		String[] commandArray = commandArgs.split(" ");

		// Command Args:
		// 0. message sender address
		// 1. request originator address
		// 2. requestID (clientID:orderID_searchNo)
		// 3-n. either the resourcs that were successfully locked (SUC) or resources that failed to be locked (FIL)

		ResourceRequest rrq = requestsInProgress.get(commandArray[2]);
		for(int i = 3; i < commandArray.length; i++){
			String resourceIdentifier = commandArray[i].split(":")[0];
			int resourceAmount = Integer.parseInt(commandArray[i].split(":")[1]);

			if(resourceAmount == 0) continue;

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

		printInfo("Missing resources: " + request.getOrderRemaining());

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
		processLockSend(getOwnAddressString(), String.valueOf(request.getIdentifier()), request.getOrderRemaining(), addresses);
	}

	public void processLockSend(String originator, String orderId, HashMap<String, Integer> requestedResources, ArrayList<String> addresses){

		printInfo("Processing lock send");
		printInfo("Addresses: " + addresses);
		printInfo("Resources: " + requestedResources);

		HashMap<String, String> partitionedResources = new HashMap<>();

		for (Entry<String, Integer> orderPart : requestedResources.entrySet()){

			int amountRemaining = requestedResources.get(orderPart.getKey());

			int requestAmount = 0;
			String remainderReceiver = "";

			if (addresses.size() > 0){

				requestAmount = (amountRemaining - (amountRemaining % addresses.size()) ) / addresses.size();
				amountRemaining = amountRemaining % addresses.size();

				remainderReceiver = addresses.get((int)Math.round(Math.random() * (addresses.size() - 1)));

				printInfo("Remainder Receiver " + remainderReceiver);

			} else {

				// Are you trying to send a lock to zero addresses?

			}

			for(String address : addresses){
				
				int amountToRequest = requestAmount;
				if(address.equals(remainderReceiver)) amountToRequest += amountRemaining;

				if(amountToRequest == 0) continue;

				if(!partitionedResources.containsKey(address))
					partitionedResources.put(address, "");

				partitionedResources.put(address, partitionedResources.get(address) + " " + orderPart.getKey() + ":" + amountToRequest);
				printInfo("Partitioned resources: " + partitionedResources);
			}
			
		}

		// we are always the search originator if we are sending LOKs
		searches.put(orderId, new NetworkSearch(orderId, partitionedResources.size(), requestedResources)); // waiting for n FINs to send to X
		printInfo("Waiting for " + partitionedResources.size() + " searches to be completed.");
		printInfo("Search: " + searches.get(orderId));

		for(Entry<String, String> res : partitionedResources.entrySet()){

				if(res.getValue().equals("") || res.getValue().equals("0")) continue;

				nodeUdpServer.send(
					"LOK " + originator + " " + orderId + res.getValue(),
					res.getKey().split(":")[0],
					Integer.parseInt(res.getKey().split(":")[1])
				);
				
				printInfo("Sent LOK to " + res.getKey() + " with values'" + res.getValue() + "'");
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

		//String[] messageText = new String[3];
		//boolean[] messageFromNode = new boolean[1];

		final ReentrantLock lock = new ReentrantLock();
		final Condition queueNotEmpty = lock.newCondition();

		LinkedList<Entry<String[], Boolean>> messageQueue = new LinkedList<>();

		nodeUdpServer = new NodeUDPServer(tcpPort);
		nodeTcpServer = new TCPServer(tcpPort);

		execSvc.submit(nodeUdpServer);
		execSvc.submit(nodeTcpServer);

		nodeTcpServer.setSilentMode(true);
		nodeUdpServer.setSilentMode(true);

		// Client message callback

		nodeTcpServer.setAutoResponse((sock, message) -> {


			String[] tempMessage = new String[3];

			tempMessage[0] = message;
			tempMessage[1] = "" + sock.getInetAddress();
			tempMessage[2] = "" + sock.getPort();

			boolean queueWasEmpty = messageQueue.isEmpty();

			messageQueue.add(new SimpleEntry<String[], Boolean>(tempMessage, false));

			if(queueWasEmpty){
				lock.lock();
				queueNotEmpty.signalAll();
				lock.unlock();
			}

			return "";

		});

		// Node message callback

		nodeUdpServer.setCallback((packet) -> {

			InetAddress incomingAdddress = packet.getAddress();

			String[] tempMessage  = new String[3];

			String receivedStr = new String(packet.getData(), 0, packet.getLength());

			tempMessage[0] = receivedStr;
			tempMessage[1] = incomingAdddress.getHostAddress();
			tempMessage[2] = "" + packet.getPort();

			boolean queueWasEmpty = messageQueue.isEmpty();

			messageQueue.add(new SimpleEntry<String[], Boolean>(tempMessage, true));

			if(queueWasEmpty){
				lock.lock();
				queueNotEmpty.signalAll();
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
			if(messageQueue.isEmpty())
			try{
				lock.lock();
				queueNotEmpty.await();
			} catch (InterruptedException e) {

			} finally {
				lock.unlock();
			}

			Entry<String[], Boolean> queueItem = messageQueue.remove();
			String[] messageText = queueItem.getKey();
			boolean messageFromNode = queueItem.getValue();

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

			if(messageFromNode){

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

	if(tcpPort == 0){
		System.out.println("Error! Did not specify port parameter.");
		System.exit(1);
	}

	if(identifier == null){
		System.out.println("Warning! Did not specify identifier! A random number will be used (may not be unique)");
		identifier = String.valueOf(Math.round(Math.random() * 1000000));
	}

	new NetworkNode(identifier, tcpPort, gatewayAddr, gatewayPort, resources);

	}
}
