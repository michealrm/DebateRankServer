package net.debaterank.server.modules;

public class PoolSizeException extends Exception {

	private static final long serialVersionUID = 4834593131450583710L;

	public PoolSizeException(int needed, int threads) {
		super("Need " + needed + " threads, but only have " + threads);
	}
	
}
