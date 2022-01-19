package io.github.vaqxai;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class NodeUDPServer extends UDPServer {

	private final ReentrantLock lock;
	private final Condition messageReceived;
	private String[] messageText;
	
	public NodeUDPServer(int port, ReentrantLock lock, Condition messageReceived, String[] messageText){
		super(port);

		this.lock = lock;
		this.messageReceived = messageReceived;
		this.messageText = messageText;

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

		try {
			buf = new byte[256];
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			socket.receive(packet);

			lock.lock();
			InetAddress incomingAdddress = packet.getAddress();

			String receivedStr = new String(packet.getData(), 0, packet.getLength());
			received.add(new Message(receivedStr, incomingAdddress.getHostAddress(), packet.getPort()));

			messageText[0] = receivedStr;
			messageText[1] = incomingAdddress.getHostAddress();
			messageText[2] = "" + packet.getPort();

			messageReceived.signalAll();
		} catch (IOException e){
			System.err.println(e);
		} finally {
			lock.unlock();
		}

	}

}
