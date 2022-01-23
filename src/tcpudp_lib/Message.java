package tcpudp_lib;

/**
 * Copied from https://github.com/vaqxai/java-tcpclientserver/
 */
public class Message {

	/** Message contents */
	private String data;

	/** Origin address */
	private String address;

	/** Origin port */
	private int port;

	/**
	 * Primary message constructor
	 * @param data message contents
	 * @param address origin address
	 * @param port origin port
	 */
	public Message(String data, String address, int port){
		this.data = data;
		this.address = address;
		this.port = port;
	}

	/**
	 *
	 * @return mesage contents
	 */
	public String getData(){
		return data;
	}

	/**
	 * 
	 * @return origin address
	 */
	public String getAddress(){
		return address;
	}

	/**
	 * 
	 * @return origin port
	 */
	public int getPort(){
		return port;
	}

	/**
	 * Returns message contents
	 */
	public String toString(){
		return data;
	}

	/**
	 * 
	 * @return origin address as string
	 */
	public String getAddrStr(){
		return address + ":" + port;
	}
	
}
