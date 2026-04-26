package com.raditomo.cli;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * `raditomo.cli.enabled=true` のとき、Spring 起動完了後に Picocli を実行する。
 *
 * raditomo シェルスクリプトは `app.jar cli ...` で起動するため、メインクラスで
 * "cli" 引数を検出して当プロパティを true にし、Web は起動しない構成にする。
 */
@Component
@ConditionalOnProperty(name = "raditomo.cli.enabled", havingValue = "true")
@RequiredArgsConstructor
public class CliRunner implements ApplicationRunner {

    private final RaditomoCli rootCommand;
    private final IFactory factory;
    private final CliExitCodeHolder exitCodeHolder;

    @Override
    public void run(ApplicationArguments args) {
        String[] cliArgs = args.containsOption("raditomo.cli.args")
                ? args.getOptionValues("raditomo.cli.args").toArray(new String[0])
                : args.getNonOptionArgs().toArray(new String[0]);
        int code = new CommandLine(rootCommand, factory).execute(cliArgs);
        exitCodeHolder.set(code);
    }
}
