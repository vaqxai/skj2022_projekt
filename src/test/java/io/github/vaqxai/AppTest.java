package io.github.vaqxai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

    @Test
    void simpleNetwork(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                NetworkNode test = new NetworkNode("#1", 7000, null, 0, "A:3 B:1");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1000);
                System.out.println("Starting network node #3");
                NetworkNode test3 = new NetworkNode("#3", 7002, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1500);
                System.out.println("Starting network node #4");
                NetworkNode test3 = new NetworkNode("#3", 7003, "localhost", 7001, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #5");
                NetworkNode test3 = new NetworkNode("#5", 7004, "localhost", 7003, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2022);
                System.out.println("Starting network node #6");
                NetworkNode test3 = new NetworkNode("#6", 7005, "localhost", 7002, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1995);
                System.out.println("Starting network node #7");
                NetworkNode test3 = new NetworkNode("#7", 7006, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #8");
                NetworkNode test3 = new NetworkNode("#8", 7007, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2011);
                System.out.println("Starting network node #9");
                NetworkNode test3 = new NetworkNode("#9", 7008, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2012);
                System.out.println("Starting network node #10");
                NetworkNode test3 = new NetworkNode("#10", 7009, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2025);
                System.out.println("Starting network node #11");
                NetworkNode test3 = new NetworkNode("#11", 7010, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();
        
        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(500);
            System.out.println("Starting network node #2");
            NetworkNode test2 = new NetworkNode("#2", 7001, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();


        try{
            Thread.sleep(10000);
            System.out.println("Time's up! Terminating the network.");
            TCPClient terminator = new TCPClient("localhost", 7000);
            terminator.send("TERMINATE");
        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }
}
