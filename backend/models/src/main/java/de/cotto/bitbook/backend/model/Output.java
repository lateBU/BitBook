package de.cotto.bitbook.backend.model;

public class Output extends InputOutput {
    public static final Output EMPTY = new Output(Coins.NONE, "");

    public Output(Coins value, String targetAddress) {
        super(value, targetAddress);
    }

}