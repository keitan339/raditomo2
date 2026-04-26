package com.raditomo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class RaditomoApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "cli".equals(args[0])) {
            // CLI モード: Web を立てず Picocli 経由でサブコマンドを実行
            String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
            SpringApplication app = new SpringApplication(RaditomoApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            System.setProperty("raditomo.cli.enabled", "true");
            // Picocli 引数は Spring の getNonOptionArgs に流したいので、
            // Spring の引数解釈を経由しないよう "--" で打ち切る形に変換
            String[] springArgs = new String[cliArgs.length + 1];
            springArgs[0] = "--";
            System.arraycopy(cliArgs, 0, springArgs, 1, cliArgs.length);
            System.exit(SpringApplication.exit(app.run(springArgs)));
        } else {
            SpringApplication.run(RaditomoApplication.class, args);
        }
    }
}
