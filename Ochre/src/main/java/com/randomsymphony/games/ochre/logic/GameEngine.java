package com.randomsymphony.games.ochre.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.randomsymphony.games.ochre.CardTableActivity;
import com.randomsymphony.games.ochre.logic.GameState.Phase;
import com.randomsymphony.games.ochre.model.Card;
import com.randomsymphony.games.ochre.model.Play;
import com.randomsymphony.games.ochre.model.Player;
import com.randomsymphony.games.ochre.model.Round;
import com.randomsymphony.games.ochre.ui.PlayerDisplay;
import com.randomsymphony.games.ochre.ui.ScoreBoard;
import com.randomsymphony.games.ochre.ui.TableDisplay;
import com.randomsymphony.games.ochre.ui.TrumpDisplay;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

public class GameEngine extends Fragment implements StateListener {

	public static final int NUMBER_OF_TRICKS = 5;
	private static final int WIN_THRESHOLD = 3;
	// number of points for making the round normally
	private static final int NUM_POINTS_MAKE = 1;
	private static final int NUM_POINTS_ALL_5 = 2;
	private static final int NUM_POINTS_ALL_5_ALONE = 4;
	private static final int NUM_POINTS_SET = 2;
	
	private static final String TAG_TRUMP_DISPLAY = "trump_display";
	private static final String TAG_GAME_STATE = "game_state";
	private static final String TAG_SCORE_DISPLAY = "score_board";
	private static final String TAG_TABLE_DISPLAY = "table_display";
	
	public static GameEngine getInstance(String trumpDisplayTag, String gameStateTag,
			String scoreBoardTag, String tableDisplayTag) {
		Bundle args = new Bundle();
		args.putString(TAG_TRUMP_DISPLAY, trumpDisplayTag);
		args.putString(TAG_GAME_STATE, gameStateTag);
		args.putString(TAG_SCORE_DISPLAY, scoreBoardTag);
		args.putString(TAG_TABLE_DISPLAY, tableDisplayTag);
		GameEngine instance = new GameEngine();
		instance.setArguments(args);
		return instance;
	}
	
	private TableDisplay mCardTable;
	/**
	 * Did I want this to map from view id to playerdisplay?
	 */
	private HashMap<Integer, PlayerDisplay> mPlayerDisplays = new HashMap<Integer, PlayerDisplay>();
	private GameState mState;
	private TrumpDisplay mTrumpDisplay;
	private ScoreBoard mScoreBoard;
	private ArrayList<StateListener> mStateListeners = new ArrayList<StateListener>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = getArguments();
		mTrumpDisplay = (TrumpDisplay) getFragmentManager().findFragmentByTag(
				args.getString(TAG_TRUMP_DISPLAY));
        mCardTable = (TableDisplay) getFragmentManager().findFragmentByTag(
                args.getString(TAG_TABLE_DISPLAY));
        mScoreBoard = (ScoreBoard) getFragmentManager().findFragmentByTag(
                args.getString(TAG_SCORE_DISPLAY));
		setGameState((GameState) getFragmentManager().findFragmentByTag(
                args.getString(TAG_GAME_STATE)));
	}

	public void registerStateListener(StateListener listener) {
		mStateListeners.add(listener);
	}
	
	public void unregisterStateListener(StateListener listener) {
		mStateListeners.remove(listener);
	}
	
	public void setPlayerDisplay(int player, PlayerDisplay display) {
		display.setGameEngine(this);
		mPlayerDisplays.put(player, display);
	}
	
	public void setTableDisplay(TableDisplay activity) {
		mCardTable = activity;
	}
	
	public void setGameState(GameState state) {
		mState = state;
		mState.setPhaseListener(this);

        updateTrumpDisplay();

        // configure player scores and captured trick counts
        updatePlayerTrickCounts();

        // give the table a reference to the new state so it can
        // appropriately handle played cards
        mCardTable.setGameState(mState);

		// configure score board
        updateScores();

        Set<Map.Entry<Integer, PlayerDisplay>> playerDisplays = mPlayerDisplays.entrySet();

        // play cards on the table display
        updateTableDisplay();

        onStateChange(mState.getGamePhase());

        if (mState.getGamePhase() == Phase.PLAY) {
            // activate the right display
            updatePlayerDisplays();
        }
	}

    private void updateScores() {
        Player[] players = mState.getPlayers();
        int team1 = mState.getPointsForPlayer(players[0]);
        team1 += mState.getPointsForPlayer(players[2]);
        int team2 = mState.getPointsForPlayer(players[1]);
        team2 += mState.getPointsForPlayer(players[3]);
        mScoreBoard.setTeamOneScore(team1);
        mScoreBoard.setTeamTwoScore(team2);
    }

    /**
     * Update the trick counts displayed for the players.
     */
    private void updatePlayerTrickCounts() {
        Round activeRound = mState.getCurrentRound();
        Player[] players = mState.getPlayers();
        // configure player displays by setting the Player on the player display,
        // redraw should be automatic
        for (int ptr = 0, limit = players.length; ptr < limit; ptr++) {
            Player target = players[ptr];
            PlayerDisplay display = mPlayerDisplays.get(ptr);
            display.setPlayer(target);
            if (activeRound != null) {
                // value may be null, beware auto-unboxing with null pointers!
                Integer trickCount = activeRound.mCapturedTrickCount.get(target);

                // the player may have no captured tricks, in which case the
                // player wasn't present in the map, and a null was returned
                // for the count above.
                if (trickCount != null) {
                    display.setTrickCount(trickCount);
                }
            }
        }
    }

    private void updateTrumpDisplay() {
        // configure trump display
        switch (mState.getGamePhase()) {
            case PICK_TRUMP:
                mTrumpDisplay.setToPickMode();
                break;
            case ORDER_UP:
                mTrumpDisplay.setToOrderUpMode();
                break;
            case DEALER_DISCARD:
            case PLAY:
                mTrumpDisplay.setToPlayMode();
                break;
            default:
                Log.w("JMATT", "Unknown game phase, " +
                        "unable to properly set trump display.");
        }
    }

    /**
     * Update the TableDisplay with the cards played in the active trick.
     */
    private void updateTableDisplay() {
        Round activeRound = mState.getCurrentRound();

        if (activeRound != null) {
            Card currentTrump = activeRound.trump;
            mCardTable.setTrumpSuit(currentTrump.getSuit());
        }

        if (activeRound != null) {
            Play[] currentTrick = activeRound.getCurrentTrick();
            if (currentTrick != null) {
                for (int ptr = 0, limit = currentTrick.length; ptr < limit; ptr++) {
                    Play cardLaid = currentTrick[ptr];
                    if (cardLaid == null) {
                        continue;
                    }

                    mCardTable.playCard(cardLaid.card, cardLaid.player);
                }
            }
        }
    }
	
	public void setTrumpDisplay(TrumpDisplay display) {
		mTrumpDisplay = display;
	}
	
	public void startGame() {
    	Player[] players = mState.getPlayers();
    	mScoreBoard.setTeamOneName(players[0].getName() + " & " + players[2].getName());
    	mScoreBoard.setTeamTwoName(players[1].getName() + " & " + players[3].getName());
		newRound();
		((CardTableActivity) getActivity()).allowNewGame(false);
	}
	
	public void newRound() {
		mState.getDeck().shuffle();
    	Player[] players = mState.getPlayers();
    	for (int ptr = 0; ptr < players.length; ptr++) {
    		players[ptr].discardHand();
    	}
    	
        GamePlayUtils.dealHand(mState.getDeck(), mState.getPlayers());
        
        // deal the possible trump card
        Card possibleTrump = mState.getDeck().deal(1)[0];
        mCardTable.setTrumpCard(possibleTrump);

        redrawAllPlayers();

        Round newRound = mState.createNewRound();
        for (PlayerDisplay display : mPlayerDisplays.values()) {
			display.setDealer(false);
			display.setMaker(false);
		}
		
		getPlayerDisplay(newRound.dealer).setDealer(true);
		// speculatively set the trump
		newRound.trump = possibleTrump;
		mState.setGamePhase(GameState.Phase.ORDER_UP);
		
		setPlayerDisplayEnabled(getNextPlayer(), true);
		
		mTrumpDisplay.setToOrderUpMode();
		mCardTable.clearPlayedCards();
	}
	
	public void playCard(Player player, Card card) {
        Round currentRound = mState.getCurrentRound();
        currentRound.addPlay(new Play(player, card));
        Log.d("JMATT", player.getName() + " played " + card.toString());

        player.discardCard(card);
        // this is wasteful, would be better to just redraw one player
        redrawAllPlayers();

        if (currentRound.totalPlays % currentRound.getActivePlayerCount() == 1) {
            mCardTable.clearPlayedCards();
        }

        mCardTable.playCard(card, player);

        //oops, need to add the card first. this logic is a little wrong

        if (currentRound.totalPlays > 0 && currentRound.isCurrentTrickComplete()) {
            finishTrick();
        }

        updatePlayerDisplays();
    }

    /**
     * Called when a trick has been completed.
     */
    private void finishTrick() {
        Round currentRound = mState.getCurrentRound();
        Play winningPlay = scoreTrick(currentRound.getLastCompletedTrick(),
                currentRound.trump.getSuit());
        int totalTricks = currentRound.addCapturedTrick(winningPlay.player);
        getPlayerDisplay(winningPlay.player).setTrickCount(totalTricks);
        Log.d("JMATT", "And the winner is: " + winningPlay.card.toString());

        if (isRoundComplete()) {
            scoreRound();
            // time for a new round
            newRound();
        } else {
            Log.d("JMATT", "Current trick is complete, starting new one.");
            // TODO we should actually only do this after trump is set and the
            // maker decides to go alone or not
            currentRound.tricks.add(new Play[currentRound.getActivePlayerCount()]);
        }
    }

    private boolean isRoundComplete() {
        Round currentRound = mState.getCurrentRound();
        return currentRound.totalPlays == currentRound.getActivePlayerCount() * NUMBER_OF_TRICKS;
    }

    /**
     * Called to set all displays to hidden and then activate the appropriate
     * one.
     */
    private void updatePlayerDisplays() {
        for (PlayerDisplay display : mPlayerDisplays.values()) {
            display.setActive(false);
        }

        setPlayerDisplayEnabled(getNextPlayer(), true);
    }

	public void setCurrentCandidateTrump(Card card) {
		// do nothing unless we're in "pick trump" phase
		if (mState.getGamePhase() != GameState.Phase.PICK_TRUMP) {
			return;
		}
		// card is ignored for now
		mTrumpDisplay.enableSetTrump();
	}
	
	/**
	 * A player passed on setting trump.
	 */
	public void pass() {
		if (mState.getGamePhase() != GameState.Phase.ORDER_UP &&
				mState.getGamePhase() != GameState.Phase.PICK_TRUMP) {
			throw new IllegalStateException("State is invalid for this operation.");
		}
		
		Round currentRound = mState.getCurrentRound();
		currentRound.trumpPasses++;
		
		// check if enough people have passed that we changed phases
		if (currentRound.trumpPasses == currentRound.getActivePlayerCount()) {
			if (mState.getGamePhase() != GameState.Phase.ORDER_UP) {
				throw new IllegalStateException("In the wrong phase to tranisition to PICK_TRUMP");
			}
			
			mState.setGamePhase(GameState.Phase.PICK_TRUMP);
			mTrumpDisplay.setToPickMode();
		} else if (currentRound.trumpPasses == currentRound.getActivePlayerCount() * 2 - 1) {
			// disable the pass button if the next player is the dealer and
			// this is the 7th pass
			mTrumpDisplay.disablePass();
		}
		
		Player currentPlayer = getNthPlayerInTrick(currentRound.dealer,
				currentRound.trumpPasses, currentRound);
		Log.d("JMATT", "Current player: " + currentPlayer.getName());
		
		// the next player is trumpPasses + 1 positions from the dealer because
		// the first passer is one position left of the dealer
		int positionFromDealer = currentRound.trumpPasses + 1;
		Player nextPlayer = getNthPlayerInTrick(currentRound.dealer, positionFromDealer,
				currentRound);
		Log.d("JMATT", "Next player: " + nextPlayer.getName());

		Log.d("JMATT", "Disabling current player.");
		// disable current player
		setPlayerDisplayEnabled(currentPlayer, false);
		
		Log.d("JMATT", "Enabling next player.");
		// activate the next player
		setPlayerDisplayEnabled(nextPlayer, true);
		redrawAllPlayers();
	}
	
	/**
	 * A player selected to set trump.
	 * @param alone Maker is going alone.
	 */
	public void setTrump(boolean alone) {
		if (mState.getGamePhase() != GameState.Phase.ORDER_UP &&
				mState.getGamePhase() != GameState.Phase.PICK_TRUMP) {
			throw new IllegalStateException("State is invalid for this operation.");
		}

		Round currentRound = mState.getCurrentRound();

		// the trump setters position from the dealer, the first person to have
		// an option to set trump is one position from the dealer
		int positionFromDealer = currentRound.trumpPasses + 1;
		Player maker = getNthPlayerInTrick(currentRound.dealer, positionFromDealer,
				currentRound);
		
		// set alone-ness after we compute which player set trump
		currentRound.alone = alone;
		currentRound.tricks.add(new Play[currentRound.getActivePlayerCount()]);
		
		// disable display of order-er
		setPlayerDisplayEnabled(maker, false);
		
		// set trump
		if (mState.getGamePhase() == GameState.Phase.ORDER_UP) {
            // trump has already been set optimistically to the dealt trump
			// card's suit, don't need to do anything else if the dealer's
			// partner is going alone

            if (!currentRound.alone || (currentRound.alone && currentRound.trumpPasses != 1)) {
				// enable display of dealer to pick a discard card
				currentRound.dealer.addCard(currentRound.trump);
				PlayerDisplay display = getPlayerDisplay(currentRound.dealer);
				display.showDiscardCard();
				setPlayerDisplayEnabled(currentRound.dealer, true);
			} else {
				startRound();
			}
		} else {
			currentRound.trump = getPlayerDisplay(maker).getSelectedCard();
			Log.d("JMATT", "trump is " + getPlayerDisplay(maker).getSelectedCard().toString());
			mState.setGamePhase(GameState.Phase.PLAY);
			startRound();
		}
		
		// set maker
		currentRound.maker = maker;
		getPlayerDisplay(currentRound.maker).setMaker(true);
		Log.d("JMATT", "Maker is: " + maker.getName());
		
		// disable trump display
		mTrumpDisplay.setToPlayMode();
		
		redrawAllPlayers();
		mCardTable.setTrumpSuit(currentRound.trump.getSuit());
	}
	
	public void discardCard(Card card) {
		Round currentRound = mState.getCurrentRound();
		currentRound.dealer.removeCard(card);
		PlayerDisplay dealer = getPlayerDisplay(currentRound.dealer);
		dealer.hideDiscardCard();
		dealer.redraw();
		
		setPlayerDisplayEnabled(currentRound.dealer, false);
		startRound();
	}

    @Override
    public void onStateChange(Phase newPhase) {
		for (int ptr = 0, limit = mStateListeners.size(); ptr < limit; ptr++) {
			mStateListeners.get(ptr).onStateChange(newPhase);
		}
	}
	
	private void startRound() {
		Player roundStarter = getNextPlayer();
		setPlayerDisplayEnabled(roundStarter, true);
		mState.setGamePhase(GameState.Phase.PLAY);
	}
	
	private PlayerDisplay getPlayerDisplay(Player forPlayer) {
		for (PlayerDisplay playerDisplay : mPlayerDisplays.values()) {
			if (playerDisplay.getPlayer() == forPlayer) {
				return playerDisplay;
			}
		}
		
		throw new RuntimeException("We shouldn't have gotten here.");
	}
	
	private void setPlayerDisplayEnabled(Player player, boolean enabled) {
		getPlayerDisplay(player).setActive(enabled);
	}
	
	private Player getNextPlayer() {
		Round currentRound = mState.getCurrentRound();
		
		if (currentRound.isCurrentTrickComplete() && 
				currentRound.totalPlays >= currentRound.getActivePlayerCount()) {
			// the next player is the winner of the last trick
			return scoreTrick(currentRound.getLastCompletedTrick(),
					mState.getCurrentRound().trump.getSuit()).player;
		} else {
			// next player is the winner of the previous trick, plus number of
			// plays in this one
			if (currentRound.totalPlays < currentRound.getActivePlayerCount()) {
				Player roundStarter = currentRound.dealer;
				int playOffset = currentRound.totalPlays + 1;
				
				// deal with the case that the dealer is sitting out
				if (currentRound.alone && currentRound.maker != currentRound.dealer) {
					Player[] allPlayers = mState.getPlayers();
					int dealerOffset = -1;
					int makerOffset = -1;
					
					for (int ptr = 0; ptr < allPlayers.length; ptr++) {
						if (allPlayers[ptr] == currentRound.maker) {
							makerOffset = ptr;
						} else if (allPlayers[ptr] == currentRound.dealer) {
							dealerOffset = ptr;
						}
					}
					
					// dealer's partner is going alone, treat player to left 
					// of dealer as the anchor point and produce an offset from there
					if (dealerOffset  % (allPlayers.length / 2) ==
							makerOffset % (allPlayers.length / 2)) {
						roundStarter = allPlayers[(dealerOffset + 1) % allPlayers.length];
						playOffset--;
					}
				}
				
				// we're still in the first trick, offset is from dealer's left
				return getNthPlayerInTrick(roundStarter, playOffset, currentRound);
			} else {
				Play lastTrickWinner = scoreTrick(currentRound.getLastCompletedTrick(),
						currentRound.trump.getSuit());
				return getNthPlayerInTrick(lastTrickWinner.player, 
						currentRound.totalPlays % currentRound.getActivePlayerCount(),
                        currentRound);
			}
		}
	}
	
	/**
	 * Returns the nth player from the start Player in a round. In doing this
	 * we consider if any players are going alone.
	 * @param start The player started the Trick
	 * @param seatsLeft How many seats to the left the desired player sits
	 * @param round The active round.
	 * @return
	 */
	private Player getNthPlayerInTrick(Player start, int seatsLeft, Round round) {
		ArrayList<Player> activePlayers = new ArrayList<Player>();
		Player[] players = mState.getPlayers();
		for (int ptr = 0; ptr < players.length; ptr++) {
			activePlayers.add(players[ptr]);
		}
		
		if (round.alone) {
			for (int loneWolf = 0; loneWolf < activePlayers.size(); loneWolf++) {
				if (activePlayers.get(loneWolf) == round.maker) {
					activePlayers.remove((loneWolf + 2) % activePlayers.size());
					break;
				}
			}
		}
		
		// find offset of the starter
		for (int ptr = 0; ptr < activePlayers.size(); ptr++) {
			if (activePlayers.get(ptr) == start) {
				return activePlayers.get((ptr + seatsLeft) % activePlayers.size());
			}
		}
		
		// dealer's partner must be going alone, return the player "behind"
		// the loaner
		for (int loneWolf = 0; loneWolf < activePlayers.size(); loneWolf++) {
			if (activePlayers.get(loneWolf) == round.maker) {
				return activePlayers.get((loneWolf + 2) % activePlayers.size());
			}
		}
		
		throw new RuntimeException("We should never have gotten here.");
	}
	
	private Play scoreTrick(Play[] trick, int trump) {
		Play winningPlay = trick[0];
		for (int ptr = 1; ptr < trick.length; ptr++) {
			if (GamePlayUtils.isGreater(trick[ptr].card, winningPlay.card,
					trick[0].card.getSuit(), trump)) {
				winningPlay = trick[ptr];
			}
		}
		
		return winningPlay;
	}
	
	private void scoreRound() {
		// TODO the round is actually tracking the number of tricks captured
		// by each player, look at that which will allow us to do less work here
		Round finishedRound = mState.getCurrentRound();
		
		HashMap<Player, Integer> winCount = new HashMap<Player, Integer>();
		// for all the tricks in the round, determine the winner
		for (int ptr = 0, limit = finishedRound.tricks.size(); ptr < limit; ptr++) {
			Play[] trick = finishedRound.tricks.get(ptr);
			Play winningPlay = scoreTrick(trick, finishedRound.trump.getSuit());
			if (winCount.containsKey(winningPlay.player)) {
				winCount.put(winningPlay.player, winCount.get(winningPlay.player) + 1);
			} else {
				winCount.put(winningPlay.player, 1);
			}
		}

		// we've got the totals, add them up for the partners
		Player[] players = mState.getPlayers();
		int makerPosition = -1;
		int[] scores = new int[players.length / 2];
		for (int ptr = 0; ptr < players.length; ptr++) {
			if (winCount.containsKey(players[ptr])) {
				scores[ptr % 2] += winCount.get(players[ptr]);
			}
			if (players[ptr] == finishedRound.maker) {
				makerPosition = ptr;
			}
		}

		if (makerPosition == -1) {
			throw new RuntimeException("Scoring error, maker not found.");
		}
		
		// determine position of the maker
		boolean wasSet = false;
		if (scores[makerPosition % 2] < WIN_THRESHOLD) {
			wasSet = true;
		}
		
		if (!wasSet) {
			int numberOfPoints;
			// check how many points the winning team got
			if (scores[makerPosition % 2] == NUMBER_OF_TRICKS) {
				if (finishedRound.alone) {
					numberOfPoints = NUM_POINTS_ALL_5_ALONE;
				} else {
					numberOfPoints = NUM_POINTS_ALL_5;
				}
			} else {
				numberOfPoints = NUM_POINTS_MAKE;
			}
			mState.addPoints(players[makerPosition % 2], numberOfPoints);
		} else {
			mState.addPoints(players[(makerPosition + 1) % 2], NUM_POINTS_SET);
		}
		
		mScoreBoard.setTeamOneScore(mState.getPointsForPlayer(players[0]));
		mScoreBoard.setTeamTwoScore(mState.getPointsForPlayer(players[1]));
	}
	
	private void redrawAllPlayers() {
		for (PlayerDisplay display : mPlayerDisplays.values()) {
			// this is wasteful, would be better to just redraw one player
			display.redraw();
		}
	}

	public void setScoreBoard(ScoreBoard scoreBoard) {
		mScoreBoard = scoreBoard;
	}
}
