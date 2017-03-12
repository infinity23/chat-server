package ChatServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import Protocol.Protocol;
import Protocol.SimpleProtocol;

public class Server {
	private static InetSocketAddress address;

	private ExecutorService exec;
	private Protocol protocol;

	private ArrayList<Message> list;
	private HashMap<String, String> map;

	public static void main(String[] args) {
		String ip = args[0];
		int port = Integer.parseInt(args[1]);
		try {
			String[] strings = ip.split("\\.");
			System.out.println(Arrays.asList(strings));
			byte[] bytes = new byte[strings.length];
			for (int i = 0; i < strings.length; i++) {
				Integer n = Integer.parseInt(strings[i]);
				bytes[i] = n.byteValue();
			}
			address = new InetSocketAddress(InetAddress.getByAddress(bytes), port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Server server = new Server();
		server.init();
	}

	public void init() {
		try {
			exec = Executors.newCachedThreadPool();
			protocol = new SimpleProtocol();
			list = new ArrayList<>();
			map = new HashMap<>();

			ServerSocket serverSocket = new ServerSocket();
			serverSocket.bind(address);
			System.out.println("Server is running.");
			while (true) {
				Socket client = serverSocket.accept();
				System.out.println("build connection");
				new HandlerThread(client);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private class HandlerThread implements Runnable {
		private Socket socket;

		public HandlerThread(Socket client) {
			socket = client;
			exec.execute(this);
		}

		public void run() {
			try {
				BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

				output.write(protocol.createMessage("welcome", "Welcome to my server.") + "\n");
				output.flush();

				Boolean signin = false;
				String username = "";

				while (true) {
					String request = input.readLine();
					String[] info = protocol.decodeMessage(request);

					if (info[0].equals("sign-up")) {
						try {
							String reason = "";
							Boolean flag = false;
							if (info[1].length() < 5 || info[1].length() > 20) {
								reason = "Username must have a length between 5 and 20. ";

							} else if (info[2].length() < 8 || info[2].length() > 32) {
								reason = "Password must have a length between 8 and 32. ";

							} else if (map.containsKey(info[1])) {
								reason = "Username is already in use.";

							} else {
								reason = "Sign up successful.";
								flag = true;
								map.put(info[1], info[2]);
							}

							output.write(protocol.createMessage(info[0], flag.toString(), reason) + "\n");
							output.close();
							input.close();
							close(socket);
							return;
						} catch (Exception e) {
							output.write(
									protocol.createMessage(info[0], "false", "Usage: type, username, password") + "\n");
							output.close();
							input.close();
							close(socket);
							return;
						}
					}

					else if (info[0].equals("sign-in")) {
						try {
							String reason = "";
							if (!map.containsKey(info[1])) {
								reason = "Username not found.";
							} else if (!map.get(info[1]).equals(info[2])) {
								reason = "Password is error.";
							} else {
								reason = "Welcome back," + info[1];
								signin = true;
								username = info[1];
							}

							output.write(protocol.createMessage(info[0], signin.toString(), reason) + "\n");
							output.flush();

							if (!signin) {
								output.close();
								input.close();
								close(socket);
								return;
							}
						} catch (Exception e) {
							output.write(
									protocol.createMessage(info[0], "false", "Usage: type, username, password") + "\n");
							output.close();
							input.close();
							close(socket);
							return;
						}

					}

					else if (info[0].equals("get-message")) {
						try {
							int offset = Integer.parseInt(info[1]);
							if (offset < -1 || offset > list.size()) {
								output.write(protocol.createMessage(info[0], "false", "Out of index.") + "\n");
								output.flush();

							} else {
								String[] messages = new String[(list.size() - offset - 1) * 4 + 1];
								messages[0] = info[0];
								for (int i = offset + 1, j = 1; i < list.size(); i++, j++) {
									messages[j] = String.valueOf(i);
									messages[++j] = list.get(i).getUsername();
									messages[++j] = list.get(i).getTime();
									messages[++j] = list.get(i).getInfo();
								}
								output.write(protocol.createMessage(messages) + "\n");
								output.flush();

							}
						} catch (Exception e) {
							output.write(protocol.createMessage(info[0], "false", "Usage: type, offset") + "\n");
							output.flush();

						}
					}

					else if (info[0].equals("send-message")) {

						try {
							Message message = new Message(username, info[1]);
							list.add(message);
							output.write(
									protocol.createMessage(info[0], "true", String.valueOf(list.size() - 1)) + "\n");
							output.flush();

						} catch (Exception e) {
							output.write(protocol.createMessage(info[0], "false", "Usage: type, content") + "\n");
							output.flush();

						}
					} else {
						output.write(protocol.createMessage("Wrong type") + "\n");
						output.flush();

					}

				}

			} catch (Exception e) {
				System.out.println("connection lost");
			} finally {
				if (socket != null) {
					try {
						socket.close();
					} catch (Exception e) {
						socket = null;
						e.printStackTrace();
					}
				}
			}
		}

		private void close(Socket socket) {
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {
					socket = null;
					e.printStackTrace();
				}
			}
		}
	}
}
