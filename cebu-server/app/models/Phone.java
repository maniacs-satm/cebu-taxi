package models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import com.google.android.gcm.server.*;

import play.db.jpa.Model;

@Entity
public class Phone extends Model {

	public String imei;
	
	@ManyToOne
	public Operator operator;
	
	@ManyToOne
	public Driver driver;
	
	@ManyToOne
	public Vehicle vehicle;
	
	public Date lastUpdate;
	
	public Boolean panic;
	    
    public Double recentLat;
    public Double recentLon;
    
    @Column(columnDefinition="TEXT")
    public String gcmKey;
    
    @Transient
    public List<MessageData> messages = new ArrayList<MessageData>();
    
    public void clearMessages()
    {
    	List<Message> m  = Message.find("fromPhone = ?", this).fetch();
    	for(Message message : m)
    	{
    		message.read = true;
    		message.save();
    	}
    }
    
    public List<LocationUpdate> getRecentUpdates(Integer number)
    {
    	List<LocationUpdate> updates = LocationUpdate.find("imei = ? order by id desc", this.imei).fetch(number);
    	
    	return updates;
    }
    
    public List<LocationUpdate> getRecentNetwork(Integer number)
    {
    	List<LocationUpdate> updates = LocationUpdate.find("imei = ? and failednetwork = true order by id desc", this.imei).fetch(number);
    	
    	return updates;
    }
    
    public List<LocationUpdate> getRecentBoot(Integer number)
    {
    	List<LocationUpdate> updates = LocationUpdate.find("imei = ? and boot = true order by id desc", this.imei).fetch(number);
    	
    	return updates;
    }
    
    public void sendMessage(String message)
    {
    	Message m  = new Message();
    	
    	m.toPhone = this;
    	m.timestamp = new Date();
    	m.read = false;
    	m.body = message;
    	
    	m.save();
    	
    	if(gcmKey != null && gcmKey != "")
    	{
    		Sender sender = new Sender("AIzaSyDeqnbKtFely_dw2nOmNloPg_KS5JclKLc");
    		com.google.android.gcm.server.Message gcmMessage = new com.google.android.gcm.server.Message.Builder().addData("timestamp", m.timestamp.toLocaleString()).addData("message", m.body).build();
    
    		try {
				Result result = sender.send(gcmMessage, gcmKey, 5);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
    
    
    public void populateUnreadMessages()
    {
    	List<Message> m = Message.find("fromPhone = ? and read = false order by timestamp", this).fetch();
    	for(Message message : m)
    	{			
    		messages.add(new MessageData(message));
    	}
    }
}
 