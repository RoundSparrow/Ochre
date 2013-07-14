package com.randomsymphony.games.ochre.logic;

import java.util.ArrayList;

import com.randomsymphony.games.ochre.model.Card;
import com.randomsymphony.games.ochre.model.Play;
import com.randomsymphony.games.ochre.model.Player;
import com.randomsymphony.games.ochre.model.Round;

import android.support.v4.app.Fragment;
import android.util.Log;

public class GameState extends Fragment {

	public static enum Phase {
		/**
		 * State right after dealing, players can choose to order up the card
		 * in the middle of the table and thus set trump.
		 */
		ORDER_UP,
		/**
		 * All players have rejected the dealt trump card, they may now pick
		 * any suit but the dealt trump as trump
		 */
		PICK_TRUMP,
		
		DEALER_DISCARD,
		/**
		 * Players are just playing tricks normally.
		 */
		PLAY,
		/**
		 * Play has not yet started, nothing has been dealt.
		 */
		NONE
	}
	
	private Player[] mPlayers = new Player[4];
	private int[] mScores = new int[] {0, 0, 0, 0};
	private PlayerFactory mPlayerSource;
	private DeckOfCards mDeck;
	private ArrayList<Round> mRounds = new ArrayList<Round>();
	private int mDealerOffset = -1;
	private Phase mGamePhase = Phase.NONE;
	private StateListener mStateListener;
	
	public GameState(PlayerFactory playerFactory) {
		mPlayerSource = playerFactory;
		initPlayers();
		mDeck = new DeckOfCards();
	}

	public Player[] getPlayers() {
		return new Player[] {mPlayers[0], mPlayers[1], mPlayers[2], mPlayers[3]};
	}
	
	public DeckOfCards getDeck() {
		return mDeck;
	}
	
	public Round createNewRound(Player dealer) {
		Log.d("JMATT", "Dealer for this round is: " + dealer.getName());
		mRounds.add(new Round(dealer));
		return getCurrentRound();
	}
	
	public Round createNewRound() {
		mDealerOffset++;
		Round round = createNewRound(mPlayers[mDealerOffset % mPlayers.length]);
		round.maker = mPlayers[(mDealerOffset + 1) % mPlayers.length];
		return round;
	}
	
	public Round getCurrentRound() {
		return mRounds.get(mRounds.size() - 1);
	}
	
	public Phase getGamePhase() {
		return mGamePhase;
	}

	public void setGamePhase(Phase gamePhase) {
		mGamePhase = gamePhase;
		if (mStateListener != null) {
			mStateListener.onStateChange(mGamePhase);
		}
	}
	
	public void setPhaseListener(StateListener listener) {
		mStateListener = listener;
	}
	
	public void addPoints(Player player, int numPoints) {
		Log.d("JMATT", "Adding " + numPoints + " to " + player.getName() + "'s team");
		for (int ptr = 0; ptr < mScores.length; ptr++) {
			if (player == mPlayers[ptr]) {
				mScores[ptr] += numPoints;
				Log.d("JMATT", "Total points: " + mScores[ptr]);
				break;
			}
		}
	}
	
	public int getPointsForPlayer(Player player) {
		for (int ptr = 0; ptr < mScores.length; ptr++) {
			if (player == mPlayers[ptr]) {
				return mScores[ptr];
			}
		}
		return -1;
	}
	
	private void initPlayers() {
		for (int count = 0; count < mPlayers.length; count++) {
			mPlayers[count] = mPlayerSource.createPlayer();
		}
	}
}