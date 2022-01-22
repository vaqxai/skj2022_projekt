package io.github.vaqxai;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;

public class NodeUDPServer extends UDPServer {

	private Consumer<DatagramPacket> callback;
	
	public NodeUDPServer(int port, Consumer<DatagramPacket> callback){
		super(port);

		this.callback = callback;

	}

	public NodeUDPServer(int port){
		super(port);
	}

	public void setCallback(Consumer<DatagramPacket> callback){
		this.callback = callback;
	}

	// This is overridden to remove a debug message.

	/**
	 * Sends string data to the supplied address. Does not modify the input string.
	 * @param data the string to be sent
	 * @param address the receiver's hostname
	 * @param port the receiver's port
	 */
	public void send(String data, String address, int port){
		InetAddress destination = null;

		try {
			destination = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			System.out.println(String.format("Unknown host %s:%s while sending packet, aborting.", address, port));
			return;
		}

		buf = data.getBytes();

		DatagramPacket packet = new DatagramPacket(buf, buf.length, destination, port);
		try {
		socket.send(packet);
		} catch (IOException e) {
			System.out.println(e);
		}

	}

	public void run(){

		while(true){

			try {
				buf = new byte[256];
				DatagramPacket packet = new DatagramPacket(buf, buf.length);
				socket.receive(packet);

				InetAddress incomingAdddress = packet.getAddress();

				String receivedStr = new String(packet.getData(), 0, packet.getLength());
				received.add(new Message(receivedStr, incomingAdddress.getHostAddress(), packet.getPort()));

				callback.accept(packet);

			} catch (IOException e){
				System.err.println(e);
			}

		}

	}

}
