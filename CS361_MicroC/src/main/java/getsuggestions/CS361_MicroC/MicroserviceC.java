package getsuggestions.CS361_MicroC;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class MicroserviceC extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField portInput;
	private JButton submitBttn;
	
	public JScrollPane scrollPane;
	public JLabel logLab;
	
	public Log log;


	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MicroserviceC frame = new MicroserviceC();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public MicroserviceC() {
		Log log = new Log(this);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 650, 700);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		// Set up labels, port input, and submit button.
		JLabel titleLab = new JLabel("Get Suggestions");
		titleLab.setHorizontalAlignment(SwingConstants.CENTER);
		titleLab.setBounds(158, 60, 319, 51);
		titleLab.setFont(new Font("Serif", Font.BOLD, 35));
		contentPane.add(titleLab);
		
		JLabel portLab = new JLabel("Port:");
		portLab.setHorizontalAlignment(SwingConstants.CENTER);
		portLab.setBounds(44, 177, 91, 43);
		portLab.setFont(new Font("Serif", Font.BOLD, 20));
		contentPane.add(portLab);
		
		portInput = new JTextField();
		portInput.setBounds(168, 177, 234, 43);
		portInput.setFont(new Font("Serif", Font.BOLD, 20));
		portInput.setColumns(10);
		contentPane.add(portInput);
		
		logLab = new JLabel("Listening on Port: ");
		logLab.setVerticalAlignment(SwingConstants.TOP);
		logLab.setBounds(90, 355, 456, 255);
		logLab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		logLab.setFont(new Font("Serif", Font.PLAIN, 15));
		
		scrollPane = new JScrollPane(logLab);
		scrollPane.setBounds(90, 355, 456, 255);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		contentPane.add(scrollPane);
		
		submitBttn = new JButton("Submit");
		submitBttn.setBounds(444, 266, 91, 43);
		submitBttn.setFont(new Font("Serif", Font.BOLD, 15));
		submitBttn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				new GetSuggestions(portInput.getText(), log);
			}
		});
		contentPane.add(submitBttn);
	}
}

class GetSuggestions extends MicroserviceC {
	private static final long serialVersionUID = 1L;
	int portNum;
	JSONObject reqObj;
	JSONObject repObj;
	JSONObject jsonObj;
	JSONArray arr;
	String date;
	String path;
	Log log;
	/**
	 * 
	 */
	
	@SuppressWarnings("unchecked")
	public GetSuggestions(String portStr, Log log) {
		byte[] req;
		ZMQ.Socket socket;
		this.log = log;
		repObj = new JSONObject();
		
		while (true) {
			// Get port number
			try {
				portNum = Integer.parseInt(portStr);
			} catch (Exception e) {
				log.addLine("Error with port number: " + e);
				e.printStackTrace();
				break;
			}
			
			// Listen
			try (ZContext context = new ZContext()) {
				// Create socket
				socket = context.createSocket(SocketType.REP);
				socket.bind("tcp://*:" + portNum);
				log.addLine("Connected to port: " + portNum);

				// Wait for request
				req = socket.recv();
			
				// Extract request
				try {
					JSONParser parser = (JSONParser) new JSONParser();
					reqObj = (JSONObject) parser.parse(new String(req, ZMQ.CHARSET));
				} catch (Exception e) {
					log.addLine("Error in extracting request");
					e.printStackTrace();
				}
				String reqStr = (String) reqObj.get("req");
				log.addLine("Recieved request: " + reqStr);
				
				
				// Fulfill request
				int result = 0;
				
				// Get all preferences
				path = (String) reqObj.get("path");
				try {
					JSONParser parser = (JSONParser) new JSONParser();
					jsonObj = (JSONObject) parser.parse(new FileReader(path));
				} catch (Exception e) {
					log.addLine("Error getting suggestions: " + e);
					e.printStackTrace();
					result = 0;
				}
				arr = (JSONArray) jsonObj.get("activities");
				date = (String) jsonObj.get("date");
				
				switch (reqStr) {
					case "getsuggestions":
						checkDate(date);
						result = getSuggestions();
						break;
					case "update":
						result = update((JSONObject) reqObj.get("obj"));
						break;
				}
				
				// Close connection if exit
				if (reqStr.equals("exit")) {
					break;
				}
				
				// Send reply
				repObj.put("code", result);
				byte[] reply = repObj.toString().getBytes(ZMQ.CHARSET);
				socket.send(reply, 0);
				log.addLine("Successfully sent reply");
	
			} catch (Exception e) {
				log.addLine("Error: " + e);
				e.printStackTrace();
				break;
			}
		}
	}
	
	// If new day, change all suggestions added to false.
	@SuppressWarnings("unchecked")
	private void checkDate(String date) {
		LocalDate dateObj = LocalDate.now();
		String dateStr = dateObj.toString();
	    if (!date.equals(dateStr)) {
	    	// Update all back to false.
	    	for (Object obj: arr) {
	    		JSONObject tempObj = (JSONObject) obj;
	    		tempObj.replace("added", false);
	    		update(tempObj);
	    	}
	    	// Update everything
	    	jsonObj.replace("activities", arr);
	    	jsonObj.replace("date", dateStr);
	    	writeJSON(jsonObj);
	    	
	    	log.addLine("Updated suggestions based on date");
	    }
	}
	
	@SuppressWarnings("unchecked")
	private int getSuggestions() {
		// Add to rejectList
		ArrayList<String> rejectList = new ArrayList<String>();
		JSONArray prefs = (JSONArray) reqObj.get("preferences");
		for (Object pref: prefs) {
			JSONObject prefObj = (JSONObject) pref;
			if (!((boolean) prefObj.get("checked"))) {
				rejectList.add((String) prefObj.get("name"));
			}
		}
		
		// Go through all suggestions and filter out.
		JSONArray results = new JSONArray();
		for (Object obj: arr) {
			JSONObject objSuggest = (JSONObject) obj;
			JSONArray arrSuggest = (JSONArray) objSuggest.get("attributes");
			
			if ((boolean) objSuggest.get("added")) {
				continue;
			}
			
			boolean reject = false;
			
			for (Object attr: arrSuggest) {
				String attrStr = (String) attr;
				if (rejectList.contains(attrStr)) {
					reject = true;
					break;
				}
			}
			if (reject) {
				continue;
			}
			results.add(objSuggest);
		}
		
		repObj.put("result", results);
		log.addLine("Successfully returned all relevant suggestions.");
		return 1;
	}
	

	// Update suggestions
	@SuppressWarnings("unchecked")
	private int update(JSONObject obj) {	
		// Loop through suggestions and update
		for (int i = 0; i < arr.size(); i++) {
			JSONObject suggestObj = (JSONObject) arr.get(i);
			String str1 = (String) suggestObj.get("name");
			String str2 = (String) obj.get("name");
			if (str1.equals(str2)) {
				arr.set(i, obj);
				break;
			}
		}
		jsonObj.replace("activities", arr);
		writeJSON(jsonObj);
		log.addLine("Updated suggestions");
		return 1;
	}
	
	// Write updated JSONObject to file.
	private void writeJSON(JSONObject obj) {
		try (FileWriter fw = new FileWriter(path)) {
			fw.write(jsonObj.toString());
		} catch (Exception e) {
			log.addLine("Error writing to suggestions file");
			e.printStackTrace();
			return;
		}
	}
}

//Keep a log
class Log {
	private String logStr;
	private MicroserviceC mainUI;
	
	public Log(MicroserviceC mainUI) {
		logStr = "";
		this.mainUI = mainUI;
	}
	
	void addLine(String line) {
		logStr += "<br/>" + line;
		mainUI.logLab.setText("<html>" + logStr + "</html");
		mainUI.scrollPane.getVerticalScrollBar().setValue(mainUI.scrollPane.getVerticalScrollBar().getMaximum());
		mainUI.revalidate();
		mainUI.repaint();
	}
}
