package multi_client_chat_application.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

//reference: https://gyawaliamit.medium.com/multi-client-chat-server-using-sockets-and-threads-in-java-2d0b64cad4a7
//reference: https://medium.com/nerd-for-tech/create-a-chat-app-with-java-sockets-8449fdaa933
//reference: https://www.geeksforgeeks.org/multi-threaded-chat-application-set-1/

public class Main {

	public static void main(String[] args) {
		
		//A concurrent HashMap to store all threads. Each "key" in the HM is a chatroom roomname, and each
		//"value" in the HM is an ArrayList of ServerThreads, one for each connected client.
		//They are stored in the HM this way to make it easy to output messages to only users in the same room
		//as the user posting a message. ConcurrentHashMap is used because each thread has its own reference
		//to the HM, and ConcurrentHashMap will facilitate synchronizing each threads requests to access it 
		ConcurrentHashMap<String, ArrayList<ServerThread>> roomThreadLists = new ConcurrentHashMap<String, ArrayList<ServerThread>>();
		//A similar concurrent HashMap, used to store message histories for all the rooms 
		ConcurrentHashMap<String, ArrayList<String>> roomMessageHistories = new ConcurrentHashMap<String, ArrayList<String>>(); 
		
		//Another concurrent HashMap used to keep track of the time of last message in each chatroom
		ConcurrentHashMap<String, LocalDate> lastRoomMessageDates = new ConcurrentHashMap<String, LocalDate> ();
		
		
		//create a default room for when clients first join 
		ArrayList<ServerThread> homeThreadList = new ArrayList<>();
		roomThreadLists.put("DefaultRoom", homeThreadList);
		
		ArrayList<String> homeMessageList = new ArrayList<>();
		roomMessageHistories.put("DefaultRoom", homeMessageList);
		
		//set the string format to use for datetimestamps which will be attached to all messages 
		SimpleDateFormat dtformat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		//get the current date and time
		Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		//convert that time stamp using the format outlined
		String ts = dtformat.format(timestamp); 

		//Add a log message of the server start to the DefaultRoom chatroom 
		roomMessageHistories.get("DefaultRoom").add("***[" + ts + "] Chatroom Application Server Started***");
		
 		//Start the server chat
		try (ServerSocket serversocket = new ServerSocket(8888)) {
			
			System.out.println("Chat Server Started.....");
			
			//wait for clients to connect, then start a serverthread to handle each of their requests 
			while(true) {
				Socket socket = serversocket.accept();
				
				System.out.println("Client thread started....");
				
				//Place new user in the DefaultRoom and pass them a reference to the list of all chatrooms and message histories 
				ServerThread serverThread = new ServerThread(socket, "DefaultRoom", roomThreadLists, roomMessageHistories, lastRoomMessageDates);
				serverThread.start();
			}
		} catch (Exception e) {
			System.out.println("Error occured in Server Main when trying to create new serversocket: " + e.getStackTrace());
		}

	}

}


