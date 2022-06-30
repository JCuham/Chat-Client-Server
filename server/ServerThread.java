package multi_client_chat_application.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//reference: https://gyawaliamit.medium.com/multi-client-chat-server-using-sockets-and-threads-in-java-2d0b64cad4a7
//reference: https://medium.com/nerd-for-tech/create-a-chat-app-with-java-sockets-8449fdaa933
//reference: https://www.geeksforgeeks.org/multi-threaded-chat-application-set-1/

public class ServerThread extends Thread {
	
	//used to send and receive data from the client
	private Socket clientSocket;
	
	//A concurrent HashMap to store all threads. Each "key" in the HM is a chatroom roomname, and each
	//"value" in the HM is an ArrayList of ServerThreads, one for each connected client.
	//They are stored in the HM this way to make it easy to output messages to only users in the same room
	//as the user posting a message. ConcurrentHashMap is used because each thread has its own reference
	//to the HM, and ConcurrentHashMap will facilitate synchronizing each threads requests to access it 
	private ConcurrentHashMap<String, ArrayList<ServerThread>> roomThreadLists;
	
	//A similar concurrent HashMap, used to store message histories for all the rooms 
	private ConcurrentHashMap<String, ArrayList<String>> roomMessageHistories;
	
	//Another concurrent HashMap used to keep track of the time of last message in each chatroom
	private ConcurrentHashMap<String, LocalDate> lastRoomMessageDates;
	
	//used to read data from the clientSocket object
	private BufferedReader in;
	
	//used to write data into the clientSocket object
	private PrintWriter out;
	
	//name of chat "room" the user in
	private String roomname;
	
	//nickname of the user
	private String nickname;
		
	//set the string format we want for datetimestamps
	private SimpleDateFormat dtformat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
	
	//current time, formatted, for outputting in messages 
	private String ts; 
	
	//used to hold the current date for use in updating the last message in lastRoomMessageDates
	//for any room this thread posts a message to
	private LocalDate currentDate;
	
	
	public ServerThread(Socket socket, 
						String roomname, 
						ConcurrentHashMap<String, ArrayList<ServerThread>> roomThreadLists, 
						ConcurrentHashMap<String, ArrayList<String>> roomMessageHistories, 
						ConcurrentHashMap<String, LocalDate> lastRoomMessageDates) {
		this.clientSocket = socket;
		this.roomThreadLists = roomThreadLists;
		this.roomMessageHistories = roomMessageHistories;
		this.lastRoomMessageDates = lastRoomMessageDates;
		this.roomname = roomname;
		this.nickname = "Unset nickname";
		this.currentDate = LocalDate.now();
		
		//add self serverthread to the room the user wants to move into
		roomThreadLists.get(roomname).add(this);
	} 
	
	@Override
	public void run() {
		try {
			//instantiate PrintWriter object out setting clientSocket as the output stream
			//to write data into (clientSocket is responsible for sending data to the client)
			//true flushes the buffer (otherwise must do it manually)
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			
			//instantiate BufferedReader object to read from clientSocket.
			//InputStreamReader creates stream reader for socket, reading the data as bytes
			//then passing it to BufferedReader to be converted to characters 
			in = new BufferedReader (new InputStreamReader(clientSocket.getInputStream(), "US-ASCII"));
			
			while(true) {
				
				//get the message from the client thread over the socket
				String outputString = in.readLine();
				
				//get the current date and time
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());
				//convert that time stamp using the format outlined and update the time for this iteration of the while loop for this thread
				ts = dtformat.format(timestamp); 
				//get the local date for todays date
				currentDate = LocalDate.now();
				
				//*************************************
				//***Inserting a chatroom with a very old date of last message to test the 'delete' functionality ****
				LocalDate testOld = LocalDate.of(2015, 6, 19);
				
				//create a new ArrayList to store the threads which will join this room
				//store it in the newroom name in the HashMap of all chatrooms
				ArrayList<ServerThread> testThreadList = new ArrayList<>();
				roomThreadLists.put("testRoomname", testThreadList);
				
				//create a new message history list for the new room and add it to roomMessageHistories
				ArrayList<String> testRoomMessageList = new ArrayList<>();
				roomMessageHistories.put("testRoomname", testRoomMessageList);
			
				//Add an entry in lastRoomMessageDates for the new room, with todays Date 
				lastRoomMessageDates.put("testRoomname", testOld);
				//*************************************
				
				
				//client will send "/nickname somenickname" to set the nickname 
				if(outputString.startsWith("/nickname ")) {
					//parse the second half of the string for the new nickname (beginning at index 10 after "/nickname ") 
					setNickname(outputString.substring(10));
					continue;
				}
				
				//client will send "/roomlist" over tunnel if they want a list of all rooms available
				else if(outputString.equals("/roomlist")) {
					
					printRoomList();					
					continue;
				}

				//client will send "/exit" to leave and delete thread
				else if(outputString.equals("/exit")) {

					clientExit();
					break; 
				}				
				
				//client will send "/room somechatroomname" to indicate user wants to move to another chat room. 
				//If "somechatroomname" already exists, client thread will be moved to that room and message history will be output.
				//else a new chatroom will be created by that name (and new message history record will be added), then the client thread will be moved to it. 
				else if(outputString.startsWith("/room ")) {
					
					leaveChatroom();					
					this.roomname = outputString.substring(6); //parse the second half of the string for the new room name (beginning at index 6 after "/room ")  
					
					if (!roomThreadLists.containsKey(roomname)) { //if chatroom does not exist yet, create and join a new one 
						
						createChatroom();
						continue;
					}
					joinChatroom(); //else, join the existing chatroom
					continue;
				}
				//if none of the above commands were given, the message sent on the socket should be output to 
				//all client threads which are in the same room as this client thread
				//output the message to all 
				processMessage(outputString);

			}
		} catch (Exception e) {
			System.out.println("Error occured in main: " + e.getStackTrace());
		}

		
	}
	public void setNickname(String nickname) { 
		
		this.nickname = nickname;
		System.out.println("Set nickname to...." + nickname);
	
	}
	
	public void printRoomList() {
		//create a set of strings to hold all of those in the roomThreadLists HashMap
		Set<String> roomList = roomThreadLists.keySet(); 
	
		//will check date of last messages in each room to ensure they
		//are not over a week old, and delete them if needed
		checkLastMessageDates();
		
		//output each chatroom name to client thread for displaying to the user
		//do not include DefaultRoom in the output, as we don't want the user moving back to that room 
		
		String roomListString = "###";	
		
		for (String room: roomList) {
			if (room != "DefaultRoom") {
				roomListString = roomListString + "-" + room;
			}
		}
		out.println(roomListString);
	}
	
	public void clientExit() {
		
		String message = "***[" + ts + "] " + this.nickname + " has left the chatroom " + this.roomname + " and quit the application***";
		System.out.println(nickname + " is disconnecting...");
		
		//remove self serverthread from room in roomThreadLists
		roomThreadLists.get(roomname).remove(this);
		
		//output message to all threads in the current chat room, announcing client is leaving the chatroom and the application
		for (ServerThread thread: roomThreadLists.get(roomname)) { 
			thread.out.println(message);
		}
		//write exit announcement message to the history for the room
		roomMessageHistories.get(roomname).add(message);
				
		//update date of last message in lastRoomMessageDates for this room  
		lastRoomMessageDates.put(roomname, currentDate);
		
		//Close the output stream and socket
		out.close();
		
		try {
		
			clientSocket.close();
		
		} catch (IOException e) {
			System.out.println("Error occured in ServerThread when trying to close clientSocket" + e.getStackTrace());
		}
		
	}
	
	public void leaveChatroom() {
		String message = "***[" + ts + "] " + this.nickname + " has left the chatroom " + this.roomname + "***";
		
		//output message to all threads in current chat room, saying client is leaving (unless in DefaultRoom, then
		//just log the message to the Histories, do not output on the thread)
		if (roomname != "DefaultRoom") {
			for (ServerThread thread: roomThreadLists.get(roomname)) {
				 
				thread.out.println(message);
				
			}
		}
		//write message to the history for the room
		roomMessageHistories.get(roomname).add(message);
		
		//update date of last message in lastRoomMessageDates for this room  
		lastRoomMessageDates.put(roomname, currentDate);
		
		//remove self serverthread from room in roomThreadLists
		roomThreadLists.get(roomname).remove(this);
	}
	
	public void createChatroom() {

		
		
		String headerMessage = "***************************************************************************";
		String headerHistoryOutput = "*******************************MESSAGE HISTORY*****************************";
		String message1 = "***[" + ts + "] Chatroom " + roomname + " has been created by " + this.nickname + "***";
		String message2 = "***[" + ts + "] " + this.nickname + " has joined the chatroom " + this.roomname + "***";
		System.out.println("Created new chat room named " + roomname + "....");
		
		//create a new ArrayList to store the threads which will join this room
		//store it in the newroom name in the HashMap of all chatrooms
		ArrayList<ServerThread> newThreadList = new ArrayList<>();
		roomThreadLists.put(roomname, newThreadList);
		
		//create a new message history list for the new room and add it to roomMessageHistories
		ArrayList<String> newRoomMessageList = new ArrayList<>();
		roomMessageHistories.put(roomname, newRoomMessageList);
		
		//add self serverthread to the room the user wants to move into
		roomThreadLists.get(roomname).add(this);
		
		//Add an entry in lastRoomMessageDates for the new room, with todays Date 
		lastRoomMessageDates.put(roomname, currentDate);
				
		//output message to current chat room, saying client is joining and new room created
		for (ServerThread thread: roomThreadLists.get(roomname)) {
 
			thread.out.println(headerMessage);
			thread.out.println(message1);
			thread.out.println(message2);
			
		}
		//write message to the history for the room
		roomMessageHistories.get(roomname).add(headerHistoryOutput);
		roomMessageHistories.get(roomname).add(message1);
		roomMessageHistories.get(roomname).add(message2);

	}
	
	public void joinChatroom() {
		
		String message = "***[" + ts + "] " + this.nickname + " has joined the chatroom " + this.roomname + "***";
		System.out.println("Moved client " + nickname + " to " + roomname + ".....");
		
		
		//add self serverthread to the room the user wants to move into
		roomThreadLists.get(roomname).add(this);
			
		//output all history messages for the newly moved to room 
		for (int i = 0; i < roomMessageHistories.get(roomname).size(); i++) {
			
			this.out.println(roomMessageHistories.get(roomname).get(i));
			
		}	
		
		//output new message to newly joined chat room, announcing new client has joined
		for (ServerThread thread: roomThreadLists.get(roomname)) {
							
			thread.out.println(message);
								
		}
		//write message to the history for the room
		roomMessageHistories.get(roomname).add(message);

		//update date of last message in lastRoomMessageDates for this room  
		lastRoomMessageDates.put(roomname, currentDate);
	}
	
	public void processMessage(String outputString) {
		
		String message = "[" + ts + "] " + "(" + this.nickname + "):" + outputString;
		System.out.println("Chatroom " + roomname + " recieved: " + message);
		
		for (ServerThread thread: roomThreadLists.get(roomname)) {
			thread.out.println(message);			
		}
		//write message to the history for the room
		roomMessageHistories.get(roomname).add(message);
		
		//update date of last message in lastRoomMessageDates for this room 
		lastRoomMessageDates.put(roomname, currentDate);
	}
	
	public void checkLastMessageDates() {
		
		LocalDate oldestPossibleDateAllowed = currentDate.minusDays(7);

		//cycle through each key, value entry in the lastRoomMessageDates ConcurrentHashMap
		for (ConcurrentHashMap.Entry<String, LocalDate> entry : lastRoomMessageDates.entrySet()) {
			String roomname_iter = entry.getKey();
			LocalDate lastDate = entry.getValue();
			
			//if the date of the last message for this chatroom is older than 7 days
			if (lastDate.isBefore(oldestPossibleDateAllowed)) {
				
				//add all inactive but connected threads in its room to DefaultRoom thread 
				for (ServerThread thread: roomThreadLists.get(roomname_iter)) {
					
					roomThreadLists.get("DefaultRoom").add(thread);	
					thread.roomname = "DefaultRoom";
				}
				
				//delete the room from roomThreadLists
				roomThreadLists.remove(roomname_iter);
				
				//delete the room from roomMessageHistories;
				roomMessageHistories.remove(roomname_iter);
				
				//delete the room from lastRoomMessageDates
				lastRoomMessageDates.remove(roomname_iter);
				
				
			}
		}		
	}

}