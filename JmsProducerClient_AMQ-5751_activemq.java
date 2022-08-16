public class JmsProducerClient {
    protected TextMessage loadJmsMessage() throws JMSException {
        try {
            // couple of sanity checks upfront
            if (client.getMsgFileName() == null) {
                throw new JMSException("Invalid filename specified.");
            }

            File f = new File(client.getMsgFileName());
            if (f.isDirectory()) {
                throw new JMSException("Cannot load from " +
                        client.getMsgFileName() +
                        " as it is a directory not a text file.");
            }

            // try to load file
            StringBuffer payload = new StringBuffer();
            try(FileReader fr = new FileReader(f);
                BufferedReader br = new BufferedReader(fr)) {
                String tmp = null;
                while ((tmp = br.readLine()) != null) {
                    payload.append(tmp);
                }
            }
            jmsTextMessage = getSession().createTextMessage(payload.toString());
            return jmsTextMessage;
        } catch (FileNotFoundException ex) {
            throw new JMSException(ex.getMessage());
        } catch (IOException iox) {
            throw new JMSException(iox.getMessage());
        }
    }

}