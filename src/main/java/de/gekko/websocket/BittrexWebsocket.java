package de.gekko.websocket;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import microsoft.aspnet.signalr.client.ConnectionState;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.hubs.HubConnection;
import microsoft.aspnet.signalr.client.hubs.HubProxy;
import microsoft.aspnet.signalr.client.transport.WebsocketTransport;

import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BittrexWebsocket {

    private static final Logger LOG = LoggerFactory.getLogger(BittrexWebsocket.class);
    private static final String DEFAULT_SERVER_URL = "https://www.bittrex.com";
    
    
    private static final microsoft.aspnet.signalr.client.Logger logger = (message, level) -> {
        // ignore all levels but critical
        if (level == LogLevel.Critical) {
            LOG.warn(message);
        }
    };
    
    
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    
    private HubConnection connection;
    private HubProxy proxy;
    private Map<String, ChannelHandler> handlers = new ConcurrentHashMap<>();
    
    Map<CurrencyPair, Object> locks = new ConcurrentHashMap<>();

    private Date connectionTimestamp;
    private WebsocketState state = WebsocketState.DISCONNECTED;
    
    public BittrexWebsocket() {
        //reconnect();
    }
    
    
    private void init() throws InterruptedException, ExecutionException {
        LOG.info("BittrexWS: Going to (re)connect.");
        handlers.clear();
        if (connection != null) {
            try {
                connection.stop();
            } catch (Exception ignored) {}
        }
        connectionTimestamp = null;
        state = WebsocketState.CONNECTING;
        connection = new HubConnection(DEFAULT_SERVER_URL, null, true, logger);
        connection.error(error -> LOG.warn("There was an error communicating with the server.", error));
        connection.connected(() -> {
            LOG.info("Connecton started");
            connectionTimestamp = new Date();
            state = WebsocketState.CONNECTED;
        });
        connection.closed(() -> {
            LOG.info("Connecton closed");
            connectionTimestamp = null;
            state = WebsocketState.DISCONNECTED;
        });
        
        proxy = connection.createHubProxy("corehub");

        proxy.subscribe(new Object() {

              @SuppressWarnings("unused")
              public void updateSummaryState(Object o) {
                  // ignore it for now
              }
              
              @SuppressWarnings("unused")
              public void updateExchangeState(UpdateExchangeStateItem o) {

                  ChannelHandler channelHandler = handlers.get(o.marketName);
                  if (channelHandler != null) {
                      try {
                          channelHandler.acceptUpdate(o);
                      } catch (Throwable t) {
                          LOG.warn("Error processing update " + o.marketName, t);
                          throw t;
                      }
                  } else {
                      LOG.warn("Received update for unknown handler, market: " + o.marketName);
                  }
              }
          });
        
        SignalRFuture<Void> start = connection.start(new WebsocketTransport(logger));
        start.get();
    }

    //TODO comments
    private <T> T useHandler(CurrencyPair currencyPair, Function<ChannelHandler, T> f) throws Exception {
        if (connection == null || connection.getState() != ConnectionState.Connected) {
            reconnect0();
        }
        if (reconnecting.get()) {
           throw new RuntimeException("Bittrex WebSocket is reconnecting...");
        }
        if (connection.getState() != ConnectionState.Connected) {
            throw new RuntimeException("Bittrex WebSocket is not connected, current state: " + connection.getState());
        }
        
        Object lock = locks.get(currencyPair);
        if (lock == null) {
            synchronized (locks) {
                lock = locks.get(currencyPair);
                if (lock == null) {
                    lock = new Object();
                    locks.put(currencyPair, lock);
                }
            }
        }
        try {
            ChannelHandler ch;
            synchronized (lock) {
                ch = subscribe(currencyPair);
            }
            return f.apply(ch);
        } catch (BittrexException e) {
            if (e.isReconnect())  {
                LOG.warn("Error, will reconnect.", e);
                reconnect0();
            } else if (e.isResubscribe()) {
                LOG.warn("Error, will resubscribe " + currencyPair, e);
                handlers.remove(toBittrexMarket(currencyPair));
            }
            throw e;
        } catch (Throwable t) {
            LOG.warn("Error " + currencyPair, t);
            throw t;
        }
    }
    
    /**
     * Gibt Orderbooks zurück
     * @param pair
     * @return
     * @throws Exception
     */
    public org.knowm.xchange.dto.marketdata.OrderBook getOrderBook(CurrencyPair pair) throws Exception {
        return useHandler(pair, h -> {
            org.knowm.xchange.dto.marketdata.OrderBook result = h.getOrderBook();
            return result;
        });
    }
    
    public void subscribeOrderbook() {
    	
    }
    
    /**
     * Gibt Trades zurück
     * @param pair
     * @return
     * @throws Exception
     */
//    public List<Trade> getTrades(CurrencyPair pair) throws Exception {
//        return useHandler(pair, h -> h.getTrades());
//    }
    
    /**
     * Reconnection routine
     */
    private void reconnect0() {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        state = WebsocketState.CONNECTING;
        connectionTimestamp = null;
        try {
            init();
            state = WebsocketState.CONNECTED;
            connectionTimestamp = new Date();
        } catch (Exception e) {
            state = WebsocketState.ERROR;
            throw new RuntimeException(e);
        } finally {
            reconnecting.set(false);
            synchronized (reconnecting) {
                reconnecting.notifyAll();
            }
        }
    }
    
    /**
     * Abonniert marktchannel für übergebenes Währungspaar
     * @param currencyPair
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private ChannelHandler subscribe(final CurrencyPair currencyPair) throws InterruptedException, ExecutionException {
    		// String umwandeln in Bittrex Marktname
        final String marketName = toBittrexMarket(currencyPair);
        // Handler für Markt abrufen
        ChannelHandler handler = handlers.get(marketName);
        if (handler != null) {
        		//Handler vorhanden also zurückgeben
            return handler;
        }
        
        //Handler nicht vorhanden also neuen Handler erstellen
        final ChannelHandler h = new ChannelHandler(marketName, currencyPair, proxy);
        handlers.put(marketName, h);
        
        SignalRFuture<Void> updates;
        try {
        		//Updates abonnieren
            updates = proxy.invoke("SubscribeToExchangeDeltas", marketName);
        } catch (Throwable e) {
            LOG.warn("Could not subscribe to " + marketName + ", going to reconnect.", e);
            reconnect();
            throw new RuntimeException();
        }
        updates.onError(err -> {
            LOG.warn("Could not subscribe for exchange deltas " + marketName, err);
            handlers.remove(marketName);
        });
        try {
            updates.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException err) {
            handlers.remove(marketName);
            LOG.warn("Could not subscribe for exchange deltas " + marketName + " (timeout).", err);
            throw new RuntimeException("Could not subscribe for exchange deltas " + marketName + " (timeout).", err);
        }
        
        h.fetchState();
        LOG.info("Subscribed successfully for " + currencyPair);
        return h;
    }
    
	public void registerSubscriber(CurrencyPair currencyPair, Updateable updateableObject) throws Exception {
        useHandler(currencyPair, h -> {
    			h.addSubscriber(updateableObject);
    			return true;
        });
	}
    
    /**
     * Wandelt CurrencyPair in Bittrex market string
     * @param pair
     * @return
     */
    protected static String toBittrexMarket(CurrencyPair pair) {
        return pair.counter + "-" + pair.base;
    }
    
    /**
     * Gibt connection timestamp zurück
     * @return
     */
    public Date connectionTimestamp() {
        return this.connectionTimestamp;
    }
    
    /**
     * reconnect
     */
    public void reconnect() {
        reconnect0();
    }

    /**
     * Gibt abonnierte Channels zurück
     * @return
     */
    public synchronized List<ChannelHandler> getChannels() {
        return handlers.values().stream().collect(Collectors.toList());
    }

    public synchronized void resubscribe(String channelId) {
        handlers.remove(channelId);
    }
    
    /**
     * Websocket states
     */
    private static enum WebsocketState {
        CONNECTED, CONNECTING, ERROR, DISCONNECTED;
    }
}
