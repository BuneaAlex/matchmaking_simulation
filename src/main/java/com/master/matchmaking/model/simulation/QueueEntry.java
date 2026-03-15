package com.master.matchmaking.model.simulation;

import com.master.matchmaking.model.entity.QueueRequestEntity;

/**
 * Snapshot of a player sitting in the matchmaking queue.
 * Tracks when they joined so we can compute wait time.
 */
public class QueueEntry
{
   private final QueueRequestEntity queueRequest;
   private final int joinTimeSeconds; // absolute second-of-day when they entered the queue

   public QueueEntry(QueueRequestEntity queueRequest, int joinTimeSeconds )
   {
      this.queueRequest = queueRequest;
      this.joinTimeSeconds = joinTimeSeconds;
   }

   public QueueRequestEntity getQueueRequest()
   {
      return queueRequest;
   }

   public int getJoinTimeSeconds()
   {
      return joinTimeSeconds;
   }

   /**
    * How many seconds this player has been waiting at the given simulation second.
    */
   public int waitTimeAt( int currentSecond )
   {
      return currentSecond - joinTimeSeconds;
   }

   /**
    * Returns true if the player has exceeded their patience at the given simulation second.
    */
   public boolean hasExpired( int currentSecond )
   {
      return waitTimeAt( currentSecond ) >= queueRequest.getPatienceSeconds();
   }
}
