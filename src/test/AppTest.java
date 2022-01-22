package io.github.vaqxai;

import org.junit.jupiter.api.Test;

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
        new NetworkNode("#1", 8000, null, 0, "A:3 B:1");
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
        new NetworkNode("#1", 9000, null, 0, "A:3 B:1");
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

        new NetworkNode("#1", 10000, null, 0, "A:3 B:1");
    }

    @Test
    void verySimpleNetwork(){
        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
        try{
                Thread.sleep(2000);
                System.out.println("Starting network node #2");
                new NetworkNode("#2", 7001, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        try{
            Thread.sleep(3000);
            System.out.println("Time's up! Terminating the network.");
            TCPClient terminator = new TCPClient("localhost", 7000);
            terminator.send("TERMINATE");
        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

    @Test
    void simpleNetwork(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1000);
                System.out.println("Starting network node #3");
                new NetworkNode("#3", 7002, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1500);
                System.out.println("Starting network node #4");
                new NetworkNode("#3", 7003, "localhost", 7001, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #5");
                new NetworkNode("#5", 7004, "localhost", 7003, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2022);
                System.out.println("Starting network node #6");
                new NetworkNode("#6", 7005, "localhost", 7002, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1995);
                System.out.println("Starting network node #7");
                new NetworkNode("#7", 7006, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #8");
                new NetworkNode("#8", 7007, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2011);
                System.out.println("Starting network node #9");
                new NetworkNode("#9", 7008, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2012);
                System.out.println("Starting network node #10");
                new NetworkNode("#10", 7009, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2025);
                System.out.println("Starting network node #11");
                new NetworkNode("#11", 7010, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();
        
        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(500);
            System.out.println("Starting network node #2");
            new NetworkNode("#2", 7001, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(2030);
            System.out.println("Starting network node #12");
            new NetworkNode("#12", 7011, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(2030);
            System.out.println("Starting network node #13");
            new NetworkNode("#13", 7012, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        while(true){}

        /*
        try{
            Thread.sleep(10000);
            System.out.println("Time's up! Terminating the network.");
            TCPClient terminator = new TCPClient("localhost", 7012);
            terminator.send("TERMINATE");
        } catch (InterruptedException e) {
            System.err.println(e);
        }
        */

    }

    @Test
    public void resourceCreationTest(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1 F:9 dupa:12837 Z:8");
            }
        }).start();

        try {
            Thread.sleep(100);
            TCPClient queryClient = new TCPClient("localhost", 7000);
            queryClient.send("RESOURCES");
            queryClient.send("LOCK A:2");
            queryClient.send("RESOURCES");
        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

    @Test
    public void simpleAllocationTest(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1 F:9 dupa:12837 Z:8");
            }
        }).start();

        try {
            Thread.sleep(100);
            TCPClient queryClient = new TCPClient("192.168.1.15", 7000);
            queryClient.send("RESOURCES");
            queryClient.send("Klient1 dupa:128 Z:900");
            Thread.sleep(500);
            queryClient.get();
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            queryClient.send("RESOURCES");
        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

    @Test
    public void partialAllocationTest(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1 F:9 dupa:12837 Z:8");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println(e);
                }
                System.out.println("Starting network node #2");
                new NetworkNode("#2", 7001, "localhost", 7000, "C:4");
            }
        }).start();

        try {
            Thread.sleep(200);
            TCPClient queryClient = new TCPClient("192.168.1.15", 7000);

            queryClient.send("Klient1 C:4");
            Thread.sleep(1000);
            queryClient.get();
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());

        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

    @Test
    public void failingPartialAllocationTest(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1 F:9 dupa:12837 Z:8");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println(e);
                }
                System.out.println("Starting network node #2");
                new NetworkNode("#2", 7001, "localhost", 7000, "C:4");
            }
        }).start();

        try {
            Thread.sleep(200);
            TCPClient queryClient = new TCPClient("192.168.1.15", 7000);

            queryClient.send("Klient1 C:4 Z:8 D:1");
            Thread.sleep(1000);
            queryClient.get();
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());

        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

    @Test
    void simpleNetworkWithReservation(){

        new Thread(new Runnable(){
            public void run(){
                System.out.println("Starting network node #1");
                new NetworkNode("#1", 7000, null, 0, "A:3 B:1");
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1000);
                System.out.println("Starting network node #3");
                new NetworkNode("#3", 7002, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1500);
                System.out.println("Starting network node #4");
                new NetworkNode("#3", 7003, "localhost", 7001, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #5");
                new NetworkNode("#5", 7004, "localhost", 7003, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2022);
                System.out.println("Starting network node #6");
                new NetworkNode("#6", 7005, "localhost", 7002, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(1995);
                System.out.println("Starting network node #7");
                new NetworkNode("#7", 7006, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2000);
                System.out.println("Starting network node #8");
                new NetworkNode("#8", 7007, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2011);
                System.out.println("Starting network node #9");
                new NetworkNode("#9", 7008, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2012);
                System.out.println("Starting network node #10");
                new NetworkNode("#10", 7009, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
                try{
                Thread.sleep(2025);
                System.out.println("Starting network node #11");
                new NetworkNode("#11", 7010, "localhost", 7000, "A:3 B:1");
                } catch (InterruptedException e) {}
            }
        }).start();
        
        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(500);
            System.out.println("Starting network node #2");
            new NetworkNode("#2", 7001, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(2030);
            System.out.println("Starting network node #12");
            new NetworkNode("#12", 7011, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        new Thread(new Runnable(){
            public void run(){
            try{
            Thread.sleep(2030);
            System.out.println("Starting network node #13");
            new NetworkNode("#13", 7012, "localhost", 7000, "A:3 B:1");
            } catch (InterruptedException e) {}
            }
        }).start();

        try {
            Thread.sleep(2000);
            TCPClient queryClient = new TCPClient("192.168.1.15", 7000);

            queryClient.send("Klient1 C:4 Z:8 D:1");
            Thread.sleep(2000);
            queryClient.get();

            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());

            queryClient.send("Klient1 A:5 B:4");
            Thread.sleep(2000);

            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());
            System.out.println("[CLIENT GOT]: " + queryClient.get());

        } catch (InterruptedException e) {
            System.err.println(e);
        }

    }

}
