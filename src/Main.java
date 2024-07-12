import com.restaurant.ClientsGroup;
import com.restaurant.Manager;
import com.restaurant.wait.NoneOnWait;
import com.restaurant.RestManager;
import com.restaurant.Table;
import java.time.Duration;
import java.util.List;

public class Main {

        public static void main(String[] args) throws InterruptedException {
        System.out.println("-------------------------------------------------------------------");
        System.out.println("""
                Hello!
                Wait a little bit, please, I'm running the tests...
                If you see a message *Assertions are enabled!*, it means all tests are passed!
                Just in case it fails in the middle, please re-run, 
                since we are testing concurrent program and only our OS decides context-switching.
                """);
        var tables = List.of(new Table(2), new Table(4));
        // You can play with these 2 options as well
//        Manager rm = new RestManager(tables, true, new SpinOnWait());
//        Manager rm = new RestManager(tables, true, new YieldOnWait());

        Manager rm = new RestManager(tables, true, new NoneOnWait());

        var cg = ClientsGroup.ofCurrentTime(1);
        var cg1 = ClientsGroup.ofCurrentTime(2);
        var cg2 = ClientsGroup.ofCurrentTime(3);
        var cg3 = ClientsGroup.ofCurrentTime(4);
        var cg4 = ClientsGroup.ofCurrentTime(5);
        var cg5 = ClientsGroup.ofCurrentTime(6);

        // nobody has seats
        assert rm.lookup(cg) == null
                && rm.lookup(cg1) == null
                && rm.lookup(cg2) == null
                && rm.lookup(cg3) == null
                && rm.lookup(cg4) == null
                && rm.lookup(cg5) == null;
        System.out.println("Passing tests ...");

        // wait time for the loop not to fail. This is for testing purposes only. In reality it does its job too quickly.
        var sleepingTime = 100;
        // cg takes a seat
        rm.onArrive(cg);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg) != null;
        assert rm.getQueueCount() == 0;
        assert rm.getSeatCount() == 1;
        System.out.println("Passing ...");

        // cg1 takes a seat
        rm.onArrive(cg1);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg1) != null;
        assert rm.getQueueCount() == 0;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg2 enters the queue
        rm.onArrive(cg2);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg2) == null;
        assert rm.getQueueCount() == 1;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg3 enters the queue
        rm.onArrive(cg3);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg3) == null;
        assert rm.getQueueCount() == 2;
        assert rm.getSeatCount() == 3;
        System.out.println("Still passing ...");

        // cg4 enters the queue
        rm.onArrive(cg4);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg4) == null;
        assert rm.getQueueCount() == 3;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg5 enters the queue
        rm.onArrive(cg5);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg5) == null;
        assert rm.getQueueCount() == 4;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg leaves
        assert rm.lookup(cg) != null;
        rm.onLeave(cg);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg) == null;
        assert rm.getQueueCount() == 4;
        assert rm.getSeatCount() == 2;
        System.out.println("Passed again ...");

        // cg1 leaves and cg2 takes a seat
        assert rm.lookup(cg1) != null;
        assert rm.lookup(cg2) == null;
        rm.onLeave(cg1);
        Thread.sleep(sleepingTime);
        assert rm.lookup(cg1) == null;
        assert rm.lookup(cg2) != null;
        assert rm.getQueueCount() == 3;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg5 abandons the queue
        assert rm.lookup(cg5) == null;
        assert rm.abandonQueue(cg5);
        assert rm.lookup(cg5) == null;
        assert rm.getQueueCount() == 2;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // nobody is awaiting more than 100 seconds
        assert !rm.abandonAllIf(Duration.ofSeconds(100));
        assert rm.getQueueCount() == 2;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // cg4 leaves, because it waited for more than 3 seconds
        assert rm.lookup(cg4) == null;
        assert rm.abandonQueueIf(cg4, Duration.ofSeconds(3));
        assert rm.lookup(cg4) == null;
        assert rm.getQueueCount() == 1;
        assert rm.getSeatCount() == 3;
        System.out.println("Passing ...");

        // Everybody leaves the queue, because they waited for more than 5 seconds
        assert rm.abandonAllIf(Duration.ofSeconds(5));
        assert rm.getQueueCount() == 0;
        assert rm.getSeatCount() == 3; // cg2
        assert rm.lookup(cg) == null
                && rm.lookup(cg1) == null
                && rm.lookup(cg2) != null // cg2 is still seated
                && rm.lookup(cg3) == null
                && rm.lookup(cg4) == null
                && rm.lookup(cg5) == null;

        System.out.println("All tests passed!");

        // to check if the assertion is enabled (-ea VM option)
        assert 0 == 1 : "Assertions are enabled!";

    }
}
