package io.github.vaqxai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author s22666, Stanisław Knapiński
 */
class AppTest {
    /**
     * Rigorous Test.
     */
    @Test
    void testNodeStartup() {
        new Thread(new Runnable(){

            public void run(){
                try{
                Thread.sleep(2000);
                UDPClient testClient = new UDPClient("localhost", 8000);
                testClient.send("TERMINATE");
                System.out.println("Test message sent!");
                } catch (Exception e){}
            }

        }).start();
        NetworkNode test = new NetworkNode("#1", 8000, null, 0, "A:3 B:1");
        System.out.println("Test network node started!");
    }

    @Test
    void testNodeTCPReceived() {

        new Thread(new Runnable(){

            public void run(){
                try{
                Thread.sleep(2000);
                TCPClient testClient = new TCPClient("localhost", 9000);
                testClient.send("TERMINATE");
                System.out.println("Test message sent!");
                } catch (Exception e){}
            }

        }).start();
        NetworkNode test = new NetworkNode("#1", 9000, null, 0, "A:3 B:1");
        System.out.println("Test network node started!");
    }

    @Test
    void testConcurrentMessages(){

        new Thread(new Runnable(){

            public void run(){
                try{
                Thread.sleep(2000);
                TCPClient testClient = new TCPClient("localhost", 10000);
                testClient.send("TERMINATOR");
                System.out.println("Test message sent!");
                } catch (Exception e){}
            }

        }).start();

        new Thread(new Runnable(){

            public void run(){
                try{
                Thread.sleep(2000);
                UDPClient testClient = new UDPClient("localhost", 10000);
                testClient.send("TERMINATE");
                System.out.println("Test message sent!");
                } catch (Exception e){}
            }

        }).start();

        NetworkNode test = new NetworkNode("#1", 10000, null, 0, "A:3 B:1");
    }

    @Test
    void verySimpleNetwork(){
        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                NetworkNode test1 = new NetworkNode("#1", 7000, null, 0, "A:3 B:1");
            }
        }).start();
        
        try{
        Thread.sleep(500);
        System.out.println("Starting network node #2");
        NetworkNode test2 = new NetworkNode("#2", 7001, "localhost", 7000, "A:3 B:1");
        } catch (InterruptedException e) {}
        
        
    }
}
