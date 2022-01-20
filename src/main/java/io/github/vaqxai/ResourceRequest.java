package io.github.vaqxai;

import java.util.HashMap;

public class ResourceRequest {
	
	public String originator = null; // which client put this order in (this stays the same even if order is fully or partially forwarded between nodes)
	HashMap<String, Integer> order = new HashMap<>(); // Resource X times Y. If one node manages to lock part of the order, it will forward a partial order to other nodes.
	HashMap<String, NetworkResource> locked = new HashMap<>(); // Resource X and resource on node that's locked by this request (the nodes do not know who locked the resources, but a request knows where it locked the resources)
	public RequestStatus status = RequestStatus.RECEIVED; // Initial status is 'received'

	/**
	 * Locks x amount of resource y on node z or less.
	 * @param toFulfillWith Node to partially/fully fulfill this order with
	 * @param amount the amount to request from the networkResource
	 * @return true if we managed to lock the full requested amount, false otherwise.
	 */
	public boolean lockAmount(NetworkResource toFulfillWith, int amount){ // We want to claim X amount of resource Y on node Z
		int lockedAmount = toFulfillWith.lock(amount);
		return this.order.get(toFulfillWith.getIdentifier()) == lockedAmount;
	}

	/**
	 * Finalizes the order, reserving all resources that it has locked.
	 * This will fail if the order status is not 'FULFILLED' aka not all resources needed are locked.
	 * @return whether the reserve succeeded (if all NetworkResources had enough resources)
	 */
	public boolean finalizeOrder(){
		for(NetworkResource lockedResource : locked.values()){
			if (lockedResource.reserve(originator, order.get(lockedResource.getIdentifier()))){
				return true;
			} else {
				return false;
			}
		}
		return false;
	}

	/**
	 * Put in a request with (this?) node for the following resources
	 * @param originator The client that originally placed the order
	 * @param order Identifier, Amount hash map
	 */
	public ResourceRequest(String originator, HashMap<String, Integer> order){
		this.originator = originator;
		this.order = order;
	}

	/**
	 * Put in a request with (this?) node for the following resource strings
	 * @param originator The client that originally placed the order
	 * @param orderStrings Formatted order string "X:# Y:# Z:#"
	 */
	public ResourceRequest(String originator, String ... orderStrings){
		this.originator = originator;

		for(String orderString : orderStrings){
			String resourceIdentifier = orderString.split(":")[0];
			int resourceAmount = Integer.parseInt(orderString.split(":")[1]);

			this.order.put(resourceIdentifier, resourceAmount);
		}
	}

}