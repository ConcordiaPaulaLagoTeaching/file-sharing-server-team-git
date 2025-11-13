package ca.concordia;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {

    public static void main(String[] args) {
        //Socket CLient
        System.out.println("Hello and welcome!");
        Scanner scanner = new Scanner(System.in);

        try {
            Socket clientSocket = new Socket("localhost", 12345);
            System.out.println("Connected to the server at localhost:12345");

            //read user input from console
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
                // Read ALL pending welcome messages
                // Read exactly one welcome message
                String welcome = reader.readLine();
                System.out.println(welcome);

                while (true) {
                    System.out.print("> ");
                    String userInput = scanner.nextLine();

                    if (userInput.isEmpty()) {
                        continue;

                    }
                    if (userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                        break;
                    }

                    writer.println(userInput);
                    System.out.println("Message sent to the server: " + userInput);

                    String response = reader.readLine();
                    System.out.println("Response from server: " + response);
                }

                // Close the socket
                clientSocket.close();
                System.out.println("Connection closed.");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                scanner.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
