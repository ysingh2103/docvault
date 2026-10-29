package com.docvault;

/**
 * Prints a salted password hash to paste into the users table.
 * Run from the app folder after "mvn package":
 *   java -cp target/classes com.docvault.PasswordTool 'a-password-you-choose'
 */
public final class PasswordTool {

    private PasswordTool() {}

    public static void main(String[] args) {
        if (args.length != 1 || args[0].length() < 8) {
            System.err.println("Usage: PasswordTool '<password of at least 8 characters>'");
            System.exit(1);
        }
        System.out.println(Auth.hash(args[0]));
    }
}
