/*
 *  ProcTxn.scala
 *  (SoundProcesses)
 *
 *  Copyright (c) 2010-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.proc

import de.sciss.osc
import collection.immutable.{ IndexedSeq => IIdxSeq, IntMap, Queue => IQueue }
import collection.breakOut
import de.sciss.synth.{osc => sosc}
import actors.DaemonActor
import concurrent.stm.{InTxnEnd, TxnExecutor, Txn, InTxn}
import sys.error
import de.sciss.synth.Server
import util.control.NonFatal

trait ProcTxn {
   import ProcTxn._

   def add( msg: osc.Message with sosc.Send, change: Option[ (FilterMode, RichState, Boolean) ], audible: Boolean,
            dependancies: Map[ RichState, Boolean ] = Map.empty, noErrors: Boolean = false ) : Unit
//   def add( player: TxnPlayer ) : Unit

   def beforeCommit( callback: ProcTxn => Unit ) : Unit
//   def beforeCommit( callback: ProcTxn => Unit, prio: Int ) : Unit
   def afterCommit( callback: ProcTxn => Unit ) : Unit
//   def afterCommit( callback: ProcTxn => Unit, prio: Int ) : Unit

   def withTransition[ T ]( trns: Transition )( thunk: => T ) : T
   def time : Double
   def transit : Transition

   private[ proc ] def ccstm : InTxn

   def isActive : Boolean = Txn.status( ccstm ) == Txn.Active
}

object ProcTxn {
   sealed abstract class FilterMode
   case object Always extends FilterMode
   case object IfChanges extends FilterMode
   case object RequiresChange extends FilterMode

   var verbose = false
   var timeoutFun : () => Unit = () => ()

//   private val localVar = new ThreadLocal[ ProcTxn ]
//   def local : ProcTxn = localVar.get

   private case class Fun( fun: () => Unit )

   private val actor = {
      val res = new DaemonActor {
         def act() { loop { react {
            case Fun( f ) => try {
               f()
            } catch {
               case NonFatal( e ) =>
                  println( "Exception in ProcTxn.spawnAtomic:" )
                  e.printStackTrace()
            }
         }}}
      }
      res.start()
      res
   }

   /**
    * Defers the execution of a transactional function to a dedicated
    * actor. This may be needed with the current implementation of
    * ScalaCollider's OSCResponders.
    *
    * It is forbidden to call this method from within an active transaction,
    * as it might result in the actor receiving multiple commands if a transaction is retried.
    */
   def spawnAtomic( block: ProcTxn => Unit ) {
      require( Txn.findCurrent.isEmpty, "Do not spawn future transactions inside a transaction" )
      actor ! Fun( () => atomic( block ))
   }

   def atomic[ Z ]( block: ProcTxn => Z ) : Z = TxnExecutor.defaultAtomic { implicit t =>
      val tx = new Impl
//      t.addWriteResource( tx, Int.MaxValue )

//      val oldTx = localVar.get
//      if( oldTx != null ) error( "Cannot nest transactions currently" )
//      localVar.set( tx )
//      try {
         block( tx )
//      } finally {
//         localVar.set( oldTx )
//      }
   }

   private val startTime    = System.currentTimeMillis // XXX eventually in logical time framework

   private val errOffMsg   = osc.Message( "/error", -1 )
   private val errOnMsg    = osc.Message( "/error", -2 )

   private class Impl( implicit txn: InTxn )
   extends ProcTxn with Txn.ExternalDecider /* WriteResource */ {
      tx =>

//      private val transitRef  = TxnLocal[ Transition ]( Instant )
      private var transitVar : Transition = Instant

      private var serverData  = Map.empty[ Server, ServerData ]
//    private var waitIDs     = Map.empty[ Server, Int ]
      private val syn         = new AnyRef
      private var entries     = IQueue.empty[ Entry ]
      private var entryMap    = Map.empty[ (RichState, Boolean), Entry ]
      private var stateMap    = Map.empty[ RichState, Boolean ]
      private var entryCnt    = 0
//      private var players     = IQueue.empty[ TxnPlayer ]

      private var beforeCommitHandlers = IIdxSeq.empty[ ProcTxn => Unit ]
//      private var afterCommitHandlers  = IIdxSeq.empty[ ProcTxn => Unit ]

      Txn.setExternalDecider( tx )
      Txn.beforeCommit( performCommit( _ ))
      Txn.afterRollback( performRollback( _ ))

      private class ServerData( val server: Server ) {
         var firstMsgs        = IQueue.empty[ osc.Message ]
         var secondMsgs       = IQueue.empty[ osc.Message ]
         var firstAbortFuns   = IQueue.empty[ () => Unit ]
         var secondAbortMsgs  = IQueue.empty[ osc.Message ]
         var waitID           = -1
         var secondSent       = false
      }

      private[ proc ] def ccstm : InTxn = txn

      // XXX eventually in logical time framework
      def time : Double = (System.currentTimeMillis - startTime) * 0.001
      def transit : Transition = transitVar

      def withTransition[ T ]( t: Transition )( thunk: => T ) : T = {
         val old = transitVar
         try {
            transitVar = t
            val res = thunk
            t.finish( tx )
            res
         } finally {
            transitVar = old
         }
      }

      // ---- WriteResource implementation ----

      def shouldCommit( implicit t: InTxnEnd ) : Boolean = syn.synchronized { /* t.status.mightCommit && { */
         if( verbose ) println( "TXN PREPARE" )
         val (clumps, maxSync) = establishDependancies
val server = Server.default // XXX vergación
         clumps.foreach( tup => {
            val (idx, msgs) = tup
            if( idx <= maxSync ) {
               val syncMsg    = server.syncMsg
               val syncID     = syncMsg.id
//               val bndl       = osc.Bundle( msgs.enqueue( syncMsg ): _* )
               val bndl       = osc.Bundle.now( (msgs :+ syncMsg): _* )
               var timeOut    = false
               val sync       = new AnyRef
               server !? (10000L, bndl, {
                  case sosc.SyncedMessage( `syncID` ) =>
                     sync.synchronized {
                        sync.notifyAll()
                     }
                  case sosc.TIMEOUT =>
                     sync.synchronized {
                        timeOut = true
                        sync.notifyAll()
                     }
               })
               sync.synchronized {
                  sync.wait()
                  if( timeOut ) {
                     timeoutFun()
                     error( "Timeout" )
                  }
               }
//
//               // XXX should use heuristic for timeouts
//               Futures.awaitAll( 10000L, fut ) match {
//                  case List( Some( true )) =>
//                  case _ =>
//                     fut.revoke()
//               }

            } else {
//               players.foreach( _.play( tx )) // XXX good spot?
               server ! osc.Bundle.now( msgs: _* ) // XXX eventually audible could have a bundle time
//               true
            }
         })
         true
      } /* } */

      private def performRollback( status: Txn.Status ) {
         if( verbose ) println( "TXN ROLLBACK" )
         val datas = serverData.values
         datas.foreach( data => {
            import data._
            if( secondSent && secondAbortMsgs.nonEmpty ) {
               server ! osc.Bundle.now( secondAbortMsgs: _* )
            }
         })
         datas.foreach( data => {
            data.firstAbortFuns.foreach( fun => try { fun() } catch { case NonFatal( e ) => e.printStackTrace() })
         })
      }

      private def performCommit( t: InTxn ) {
         if( verbose ) println( "TXN COMMIT" )
         val datas = serverData.values
         datas.foreach( data => {
            import data._
//            players.foreach( _.play( tx ))
            if( !secondSent && secondMsgs.nonEmpty ) {
               server ! osc.Bundle.now( secondMsgs: _* )
            }
         })
         beforeCommitHandlers.foreach( _.apply( tx ))
      }

      def waitFor( server: Server, ids: Int* ) {
         val data = getServerData( server )
         data.waitID = math.max( data.waitID, ids.max )
      }

      private def getServerData( server: Server ) =
         serverData.getOrElse( server, {
            val data = new ServerData( server )
            serverData += server -> data
            data
         })

//      def add( player: TxnPlayer ) : Unit = syn.synchronized {
//         players = players enqueue player
//      }

      def add( msg: osc.Message with sosc.Send, change: Option[ (FilterMode, RichState, Boolean) ], audible: Boolean,
               dependancies: Map[ RichState, Boolean ], noError: Boolean = false ) { syn.synchronized {

         if( verbose ) println( "TXN ADD : " + (msg, change, audible, dependancies, noError) )

         def processDeps : Entry = {
            dependancies foreach { tup =>
               val (state, _) = tup
               if( !stateMap.contains( state )) {
                  stateMap += state -> state.get( tx )
               }
            }
            val entry = Entry( entryCnt, msg, change, audible, dependancies, noError )
            entryCnt += 1
            entries = entries.enqueue( entry )
            entry
         }

         change.map( tup => {
            val (mode, state, value) = tup
            val changed = state.get( tx ) != value
            require( changed || (mode != RequiresChange) )
            if( changed || (mode == Always) ) {
               // it is important that processDeps is
               // executed before state.set as the object
               // might depend on a current state of its own
               val entry = processDeps
               entryMap += (state, value) -> entry
               if( changed ) state.set( value )( tx )
            }
         }).getOrElse( processDeps )
      }}

      def beforeCommit( callback: ProcTxn => Unit ) {
//         txn.beforeCommit( _ => callback( tx ))
         beforeCommitHandlers :+= callback // .transform( _ :+ callback )( tx )
      }

//      def beforeCommit( callback: ProcTxn => Unit, prio: Int ) {
//         txn.beforeCommit( _ => callback( tx ), prio )
//      }

      def afterCommit( callback: ProcTxn => Unit ) {
//         txn.afterCommit( _ => callback( tx ))
         Txn.afterCommit( _ => callback( tx ))
      }

//      def afterCommit( callback: ProcTxn => Unit, prio: Int ) {
//         txn.afterCommit( _ => callback( tx ), prio )
//      }

      // XXX IntMap lost. might eventually implement the workaround
      // by jason zaugg : http://gist.github.com/452874
      private def establishDependancies : (Map[ Int, IIdxSeq[ osc.Message ]], Int) = {
         var topo = Topology.empty[ Entry, EntryEdge ]

         var clumpEdges = Map.empty[ Entry, Set[ Entry ]]

         entries.foreach( targetEntry => {
            topo = topo.addVertex( targetEntry )
            targetEntry.dependancies.foreach( dep => {
               entryMap.get( dep ).map( sourceEntry => {
                  val edge = EntryEdge( sourceEntry, targetEntry )
                  topo.addEdge( edge ) match {
                     case Some( (newTopo, _, _) ) => {
                        topo = newTopo
                        // clumping occurs when a synchronous message depends on
                        // an asynchronous message
                        if( !sourceEntry.msg.isSynchronous && targetEntry.msg.isSynchronous ) {
                           clumpEdges += targetEntry -> (clumpEdges.getOrElse( targetEntry, Set.empty ) + sourceEntry)
                        }
                     }
                     case None => {
                        error( "Unsatisfied dependancy " + edge )
                     }
                  }
               }).getOrElse({
                  val (state, value) = dep
                  if( stateMap.get( state ) != Some( value )) {
                     error( "Unsatisfied dependancy " + dep )
                  }
               })
            })
         })

         // clumping
         var clumpIdx   = 0
         var clumpMap   = Map.empty[ Entry, Int ]
//         var clumps     = IntMap.empty[ IQueue[ osc.Message ]]
         var clumps     = IntMap.empty[ List[ Entry ]]
         val audibleIdx = Int.MaxValue
         topo.vertices.foreach( targetEntry => {
            if( targetEntry.audible ) {
//               clumps += audibleIdx -> (clumps.getOrElse( audibleIdx, IQueue.empty ) enqueue targetEntry.msg)
               clumps += audibleIdx -> (targetEntry :: clumps.getOrElse( audibleIdx, Nil ))
               clumpMap += targetEntry -> audibleIdx
            } else {
               val depIdx = clumpEdges.get( targetEntry ).map( set => {
                  set.map( clumpMap.getOrElse( _, error( "Unsatisfied dependancy " + targetEntry ))).max
               }).getOrElse( -1 )
               if( depIdx > clumpIdx ) error( "Unsatisfied dependancy " + targetEntry )
               if( depIdx == clumpIdx ) clumpIdx += 1
//               clumps += clumpIdx -> (clumps.getOrElse( clumpIdx, IQueue.empty ) enqueue targetEntry.msg)
               clumps += clumpIdx -> (targetEntry :: clumps.getOrElse( clumpIdx, Nil ))
               clumpMap += targetEntry -> clumpIdx
            }
         })

         if( verbose ) clumps.foreach( tup => {
            val (idx, msgs) = tup
            println( "clump #" + idx + " : " + msgs.toList )
         })

         val sorted: Map[ Int, IIdxSeq[ osc.Message ]] = clumps mapValues { entries =>
            var noError = false
            entries.sortWith( (a, b) => {
               // here comes the tricky bit:
               // preserve dependancies, but also
               // entry indices in the case that there
               // are no indices... we should modify
               // topology instead eventually XXX
               val someB = Some( b )
               val someA = Some( a )
               val adep  = a.dependancies.exists( tup => entryMap.get( tup ) == someB )
               if( !adep ) {
                  val bdep = b.dependancies.exists( tup => entryMap.get( tup ) == someA )
                  if( !bdep ) a.idx < b.idx
                  else true
               } else false

            }).flatMap( entry => {
               if( entry.noError == noError ) {
                  List( entry.msg )
               } else {
                  noError = !noError
                  List( if( noError ) errOffMsg else errOnMsg, entry.msg )
               }
            })( breakOut )
         }
         (sorted, if( clumps.contains( audibleIdx )) clumpIdx else clumpIdx - 1)
      }
   }

   private case class Entry( idx: Int, msg: osc.Message with sosc.Send,
                             change: Option[ (FilterMode, RichState, Boolean) ],
                             audible: Boolean, dependancies: Map[ RichState, Boolean ],
                             noError: Boolean )

//   private object EntryOrdering extends Ordering[ Entry ] {
//      def compare( a: Entry, b: Entry ) = a.idx.compare( b.idx )
//   }

   private case class EntryEdge( sourceVertex: Entry, targetVertex: Entry ) extends Topology.Edge[ Entry ]
}