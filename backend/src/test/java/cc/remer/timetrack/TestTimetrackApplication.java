package cc.remer.timetrack;

import org.springframework.boot.SpringApplication;

public class TestTimetrackApplication {

  public static void main(String[] args) {
    SpringApplication.from(TimetrackApplication::main).with(TestcontainersConfiguration.class).run(args);
  }
}
