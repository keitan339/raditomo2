package com.raditomo.cli;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class CliExitCodeHolder implements ExitCodeGenerator {
    private int code = 0;
    public void set(int code) { this.code = code; }
    @Override public int getExitCode() { return code; }
}
