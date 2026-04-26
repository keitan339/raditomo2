package com.raditomo.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

@Component
@Command(
        name = "raditomo",
        mixinStandardHelpOptions = true,
        version = "raditomo 0.1.0",
        description = "Raditomo CLI",
        subcommands = {
                UsersCommand.class
                // download / download-programs / download-audio はフェーズ4で追加
        }
)
public class RaditomoCli implements Runnable {
    @Override
    public void run() {
        // ルートコマンドのみ呼ばれた場合は help を促す
        System.out.println("Use --help to see available commands");
    }
}
