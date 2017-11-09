package de.gekko.websocket;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.hubs.HubProxy;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.LimitOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(ChannelHandler.class);
    
    private WebsocketChannelState state = WebsocketChannelState.SYNCING;
    
    private CurrencyPair currencyPair;
    private Set<Updateable> subscribers = new HashSet<>();
    private ExecutorService broadcastExecutorService;
  
    private final TreeMap<BigDecimal, LimitOrder> asks = new TreeMap<>();
    private final TreeMap<BigDecimal, LimitOrder> bids = new TreeMap<>((k1, k2) -> -k1.compareTo(k2));
    
    private final HubProxy proxy;
    private final String marketName;
    
    private OrderBook orderBook = null;

    private long heartbeat;
    private Date syncTimestamp;
    private long nounce;
    
    // warteschlange für Updateverarbeitung
    private final List<UpdateExchangeStateItem> queue = new ArrayList<>();
    // speichert vergangene trades
//    private final RingBuffer<Trade> tradeRing = new RingBuffer<Trade>(1000);
    
    public ChannelHandler(String marketName, CurrencyPair currencyPair, HubProxy proxy) {
        this.currencyPair = currencyPair;
        this.proxy = proxy;
        this.marketName = marketName;
    }
    
    protected void fetchState() {
        SignalRFuture<QueryExchangeState> state = proxy.invoke(QueryExchangeState.class, "QueryExchangeState", marketName);
        
        try {
            QueryExchangeState queryExchangeState = state.get(10, TimeUnit.SECONDS);
            if (queryExchangeState == null) {
                throw new RuntimeException("Exchange State in null, pair " + currencyPair + ".");
            }
            processSnapShot(queryExchangeState);
            getOrderBook();
        } catch (Throwable e) {
            LOG.warn("Could not fetch the snapshot " + e.getClass().getSimpleName(), e);
            this.state = WebsocketChannelState.ERROR;
            throw new BittrexException(false, "Could not fetch the snapshot. " + e.getClass().getSimpleName()  + ": "+ e.getMessage(), true);
        }
    }
    
    /**
     * Accepts updates from the exchange. Updates are put in queue if channel is syncing, otherwise updates are processed.
     * @param o
     */
    synchronized protected void acceptUpdate(UpdateExchangeStateItem o) {
    	LOG.info("updating orderbook " + currencyPair);
        orderBook = null;
        if (state == WebsocketChannelState.SYNCING) {
            queue.add(o);
        } else {
            processUpdate(o);
        }
        heartbeat = System.currentTimeMillis();
    }
    
    /**
     * Processes updates from the exchange.
     * @param o
     */
    private void processUpdate(UpdateExchangeStateItem o) {
        if (o.nounce <= nounce) {
        		// nounce des updates älter als aktuelle nounce
            return;
        }
        if (o.nounce - nounce == 1) {
            nounce++;
        } else {
            LOG.warn("Missing data, going to resubscribe " + currencyPair);
            state = WebsocketChannelState.ERROR;
            syncTimestamp = null;
            orderBook = null;
            return;
        }
        
        // processes bids updates
        BiConsumer<OrderUpdate, TreeMap<BigDecimal, LimitOrder>> bidsProcessor = (update, bids) -> {
            switch (update.type) {
            case ADD:
            case UPDATE:
                bids.put(update.rate, new LimitOrder(Order.OrderType.BID, update.quantity, currencyPair, null, null, update.rate));
                break;
            case REMOVE:
                bids.remove(update.rate);
                break;
            default:
                throw new RuntimeException("Unknown update type " + update.type);    // should never happen
            }  
        };
        
        // processes asks updates
        BiConsumer<OrderUpdate, TreeMap<BigDecimal, LimitOrder>> asksProcessor = (update, asks) -> {
            switch (update.type) {
            case ADD:
            case UPDATE:
                asks.put(update.rate, new LimitOrder(Order.OrderType.ASK, update.quantity, currencyPair, null, null, update.rate));
                break;
            case REMOVE:
                asks.remove(update.rate);
                break;
            default:
                throw new RuntimeException("Unknown update type " + update.type);    // should never happen
            }  
        };
        
        // feed processors with updates
        Stream.of(o.buys).forEach(update -> bidsProcessor.accept(update, bids));
        Stream.of(o.sells).forEach(update -> asksProcessor.accept(update, asks));
        
        broadcast();
        
        //TODO REMOVE OLD CODE
//        Stream.of(o.fills).forEach(u -> {
//            OrderType ordeType =  u.orderType.equals("SELL") ? OrderType.ASK : OrderType.BID;
//            tradeRing.add(new Trade(ordeType, u.quantity, pair, u.rate, u.timeStamp, null));
//        });
    }
    
    /**
     * 
     * @param v
     */
    private synchronized void processSnapShot(QueryExchangeState v) {
        nounce = v.nounce;
        bids.clear();
        asks.clear();
        Stream.of(v.buys).forEach(o -> bids.put(o.rate, new LimitOrder(Order.OrderType.BID, o.quantity, currencyPair, null, null, o.rate)));
        Stream.of(v.sells).forEach(o -> asks.put(o.rate, new LimitOrder(Order.OrderType.ASK, o.quantity, currencyPair, null, null, o.rate)));
        
        queue.forEach(this::processUpdate);
        queue.clear();
        
//        tradeRing.clear();
//        Stream.of(v.fills).forEach(f -> {
//            String id = Long.toString(f.id);
//            OrderType ordeType =  f.orderType.equals("SELL") ? OrderType.ASK : OrderType.BID;
//            tradeRing.add(new Trade(ordeType, f.quantity, pair, f.price, f.timeStamp, id));
//        });
        
        state = WebsocketChannelState.SYNCED;
        heartbeat = System.currentTimeMillis();
        syncTimestamp = new Date();
    }
    //subscriber.receiveUpdate(new OrderBookUpdate(currencyPair, getOrderBook())))
	public void broadcast() {
		if(!subscribers.isEmpty()) {
			broadcastExecutorService = Executors.newFixedThreadPool(subscribers.size());
			subscribers.forEach(subscriber -> broadcastExecutorService.submit(
					() -> {subscriber.receiveUpdate(new OrderBookUpdate(currencyPair, getOrderBook()));
					System.out.println("broadcasted...");
					}));
			broadcastExecutorService.shutdown();
		}
	}

    public synchronized org.knowm.xchange.dto.marketdata.OrderBook getOrderBook() {
        checkState();
        
        org.knowm.xchange.dto.marketdata.OrderBook old = orderBook;
        if (old != null) {
            return old;
        }
        
//        synchronized (this) {
            orderBook = new org.knowm.xchange.dto.marketdata.OrderBook(null , new ArrayList<>(asks.values()), new ArrayList<>(bids.values()));
            
//            checkConsistency(orderBook);
            return orderBook;
//        }
    }

//    private void checkConsistency(OrderBook orderBook) {
//        if (orderBook.bids.isEmpty()) {
//            throw new BittrexException(false, String.format("Order book inconsistent, pair: %s, bid site is empty.", pair), true);
//        }
//        if (orderBook.asks.isEmpty()) {
//            throw new BittrexException(false, String.format("Order book inconsistent, pair: %s, ask site is empty.", pair), true);
//        }
//        if (gt(orderBook.bids.firstKey(), orderBook.asks.firstKey())) {
//            throw new BittrexException(false, String.format("Order book inconsistent, pair: %s, first bid %s is higher than first ask %s.", pair, orderBook.bids.firstKey(), orderBook.asks.firstKey()), true);
//        }        
//    }
    
    public static boolean gt(BigDecimal a, BigDecimal b) { return a.compareTo(b) > 0; }
    

//    public List<Trade> getTrades() {
//        checkState();
//        synchronized (this) {
//            return tradeRing.list();
//        }
//    }

    private void checkState() {
        if (!state.synced()) {
            throw new BittrexException(false, "Channel is not synced @ bittrex, pair: " + currencyPair + ", state: " + state, state == WebsocketChannelState.ERROR);
        } else if(System.currentTimeMillis() - heartbeat > TimeUnit.SECONDS.toMillis(60)) {
            throw new BittrexException(false, "Channel has not received updates @ bittrex, pair: " + currencyPair + ", state: " + state, true);
        }
    }

    public String getId() {
        return marketName;
    }

    public WebsocketChannelState getState() {
        return state;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }
    public Date getSyncTimestamp() {
        return syncTimestamp;
    }
    
    public void addSubscriber(Updateable updateableObject) {
    		subscribers.add(updateableObject);
    }
    
    public void removeSubscriber(Updateable updateableObject) {
		subscribers.remove(updateableObject);
    }

    public int getTimeSinceLastUpdateSeconds() {
        return heartbeat == 0 ? -1 : (int) ((System.currentTimeMillis() - heartbeat) / 1000);
    }

    public static enum WebsocketChannelState {
        SYNCED, SYNCING, ERROR;

        public boolean synced() {
            return this == SYNCED;
        }
    }
}
