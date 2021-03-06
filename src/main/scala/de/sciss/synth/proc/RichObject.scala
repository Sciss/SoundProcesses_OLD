/*
 *  RichObject.scala
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

import collection.breakOut
import collection.immutable.{IndexedSeq => IIdxSeq}
import ProcTxn._
import de.sciss.synth.{ addToHead, AddAction, Buffer, ControlABusMap, ControlKBusMap, ControlSetMap,
   Group, Node, Server, Synth, SynthDef, SynthGraph }
import de.sciss.synth.io.{AudioFileType, SampleFormat}
import util.control.NonFatal

trait RichObject { def server: Server }

case class RichBuffer( buf: Buffer ) extends RichObject {
   val isOnline: RichState   = new RichState( this, "isOnline", false )
   val hasContent: RichState = new RichState( this, "hasContent", false )

   def server = buf.server

   def alloc( numFrames: Int, numChannels: Int = 1 )( implicit tx: ProcTxn ) {
      tx.add( buf.allocMsg( numFrames, numChannels ), Some( (RequiresChange, isOnline, true) ), audible = false )
   }

   def cue( path: String, startFrame: Int = 0 )( implicit tx: ProcTxn ) {
      tx.add( buf.cueMsg( path, startFrame ), Some( (Always, hasContent, true) ), audible = false, dependancies = Map( isOnline -> true ))
   }

   def record( path: String, fileType: AudioFileType, sampleFormat: SampleFormat )( implicit tx: ProcTxn ) {
      tx.add( buf.writeMsg( path, fileType, sampleFormat, 0, 0, leaveOpen = true ),
         Some( (Always, hasContent, true) ), audible = false, dependancies = Map( isOnline -> true )) // hasContent is a bit misleading...
   }

   def zero( implicit tx: ProcTxn ) {
      tx.add( buf.zeroMsg, Some( (Always, hasContent, true) ), audible = false, dependancies = Map( isOnline -> true ))
   }
}

object RichNode {
   private val EmptyOnEnd = new OnEnd( IIdxSeq.empty, IIdxSeq.empty )
   private final case class OnEnd( direct: IIdxSeq[ () => Unit ], inTxn: IIdxSeq[ ProcTxn => Unit ]) {
      def nonEmpty = direct.nonEmpty || inTxn.nonEmpty
   }
}
abstract class RichNode( val initOnline : Boolean ) extends RichObject {
   import RichNode._

   val isOnline: RichState = new RichState( this, "isOnline", initOnline )
//   private val onEndFuns   = Ref( IQueue.empty[ Function1[ ProcTxn, Unit ]])
   private val onEndFuns   = Ref( EmptyOnEnd )

   // ---- constructor ----
   node.onEnd {
      ProcTxn.atomic { implicit tx =>
         isOnline.set( false )
         val e = onEndFuns.swap( EmptyOnEnd )
         if( e.nonEmpty ) {
            tx.afterCommit { _ =>
               if( e.inTxn.nonEmpty ) ProcTxn.spawnAtomic { implicit tx =>
                  e.inTxn.foreach { f => try {
                     f( tx )
                  } catch {
                     case NonFatal( ex ) => ex.printStackTrace()
                  }}
               }
               if( e.direct.nonEmpty ) {
                  e.direct.foreach { f => try {
                     f()
                  } catch {
                     case NonFatal( ex ) => ex.printStackTrace()
                  }}
               }
            }
         }
      }
   }

//   node.onEnd {
//      ProcTxn.spawnAtomic { implicit tx =>
//         val funs       = onEndFuns.swap( IQueue.empty )
//         funs.foreach( f => try {
//            f( tx )
//         } catch { case e => e.printStackTrace })
//      }
//   }

   def onEndTxn( fun: ProcTxn => Unit )( implicit tx: ProcTxn ) {
      onEndFuns.transform { e => e.copy( inTxn = e.inTxn :+ fun )}
   }

   def onEnd( code: => Unit )( implicit tx: ProcTxn ) {
      onEndFuns.transform { e => e.copy( direct = e.direct :+ (() => code) )}
   }

////   def onEnd( fun: ProcTxn => Unit )( implicit tx: ProcTxn )
//   def onEnd( code: => Unit )( implicit tx: ProcTxn ) {
//      onEndFuns.transform { queue =>
////         if( queue.isEmpty ) node.onEnd {
////            ProcTxn.spawnAtomic { implicit tx =>
////// since we are now executing the txn only when there are client
////// onEnd functions, it doesn't make sense to re-set the isOnline.
////// i don't think it should be used anyways, as nodes are
////// better created anew each time instead of reusing old ids.
//////               val wasOnline  = isOnline.swap( false )
////               val funs       = onEndFuns.swap( IQueue.empty )
////               funs.foreach( f => try {
////                  f( tx )
////               } catch { case e => e.printStackTrace })
////            }
////         }
//         queue.enqueue( () => code )
//      }
//   }

   def node: Node

   def server = node.server

   def read( assoc: (RichAudioBus, String) )( implicit tx: ProcTxn ) : AudioBusNodeSetter = {
      val (rb, name) = assoc
      val reader = BusNodeSetter.reader( name, rb, this )
      registerSetter( reader )
      reader
   }

   def read( assoc: (RichControlBus, String) )( implicit tx: ProcTxn ) : ControlBusNodeSetter = {
      val (rb, name) = assoc
      val reader = BusNodeSetter.reader( name, rb, this )
      registerSetter( reader )
      reader
   }

   def write( assoc: (RichAudioBus, String) )( implicit tx: ProcTxn ) : AudioBusNodeSetter = {
      val (rb, name) = assoc
      val writer = BusNodeSetter.writer( name, rb, this )
      registerSetter( writer )
      writer
   }

   def write( assoc: (RichControlBus, String) )( implicit tx: ProcTxn ) : ControlBusNodeSetter = {
      val (rb, name) = assoc
      val writer = BusNodeSetter.writer( name, rb, this )
      registerSetter( writer )
      writer
   }
   
   def readWrite( assoc: (RichAudioBus, String) )( implicit tx: ProcTxn ) : AudioBusNodeSetter = {
      val (rb, name) = assoc
      val rw = BusNodeSetter.readerWriter( name, rb, this )
      registerSetter( rw )
      rw
   }

   def readWrite( assoc: (RichControlBus, String) )( implicit tx: ProcTxn ) : ControlBusNodeSetter = {
      val (rb, name) = assoc
      val rw = BusNodeSetter.readerWriter( name, rb, this )
      registerSetter( rw )
      rw
   }

   def map( assoc: (RichAudioBus, String) )( implicit tx: ProcTxn ) : AudioBusNodeSetter = {
      val (rb, name) = assoc
      val mapper = BusNodeSetter.mapper( name, rb, this )
      registerSetter( mapper )
      mapper
   }

   def map( assoc: (RichControlBus, String) )( implicit tx: ProcTxn ) : ControlBusNodeSetter = {
      val (rb, name) = assoc
      val mapper = BusNodeSetter.mapper( name, rb, this )
      registerSetter( mapper )
      mapper
   }

   private def registerSetter( bns: BusNodeSetter )( implicit tx: ProcTxn ) {
      bns.add
      onEndTxn { tx0 => bns.remove( tx0 )}
   }

   def free( audible: Boolean = true )( implicit tx: ProcTxn ) {
      tx.add( node.freeMsg, Some( (IfChanges, isOnline, false) ), audible, Map( isOnline -> true ))
   }

   def set( audible: Boolean, pairs: ControlSetMap* )( implicit tx: ProcTxn ) {
      tx.add( node.setMsg( pairs: _* ), None, audible, Map( isOnline -> true ))
   }

   def setIfOnline( pairs: ControlSetMap* )( implicit tx: ProcTxn ) {
      // XXX eventually this should be like set with different failure resolution
      if( isOnline.get ) tx.add( node.setMsg( pairs: _* ), None, audible = true, noErrors = true )
//      if( isOnline.get ) tx.add( OSCBundle(
//         OSCMessage( "/error", -1 ), node.setMsg( pairs: _* ), OSCMessage( "/error", -2 )), true )
   }

   def mapn( audible: Boolean, pairs: ControlKBusMap* )( implicit tx: ProcTxn ) {
      tx.add( node.mapnMsg( pairs: _* ), None, audible, Map( isOnline -> true ))
   }

   def mapan( audible: Boolean, pairs: ControlABusMap* )( implicit tx: ProcTxn ) {
      tx.add( node.mapanMsg( pairs: _* ), None, audible, Map( isOnline -> true ))
   }

   def moveToHead( audible: Boolean, group: RichGroup )( implicit tx: ProcTxn ) {
      tx.add( node.moveToHeadMsg( group.group ), None, audible, Map( isOnline -> true, group.isOnline -> true ))
   }

   def moveToHeadIfOnline( group: RichGroup )( implicit tx: ProcTxn ) {
      if( isOnline.get ) tx.add( node.moveToHeadMsg( group.group ), None, audible = true, Map( group.isOnline -> true ), noErrors = true )
   }

   def moveToTail( audible: Boolean, group: RichGroup )( implicit tx: ProcTxn ) {
      tx.add( node.moveToTailMsg( group.group ), None, audible, Map( isOnline -> true, group.isOnline -> true ))
   }

   def moveBefore( audible: Boolean, target: RichNode )( implicit tx: ProcTxn ) {
      tx.add( node.moveBeforeMsg( target.node ), None, audible, Map( isOnline -> true, target.isOnline -> true ))
   }

   def moveAfter( audible: Boolean, target: RichNode )( implicit tx: ProcTxn ) {
      tx.add( node.moveAfterMsg( target.node ), None, audible, Map( isOnline -> true, target.isOnline -> true ))
   }
}

case class RichSynth( synth: Synth, synthDef: RichSynthDef ) extends RichNode( false ) {
   def node: Node = synth

   def play( target: RichNode, args: Seq[ ControlSetMap ] = Nil, addAction: AddAction = addToHead,
             bufs: Seq[ RichBuffer ] = Nil )( implicit tx: ProcTxn ) {

      require( target.server == server )
      bufs.foreach( b => require( b.server == server ))

      val deps: Map[ RichState, Boolean ] = bufs.map( _.hasContent -> true )( breakOut )      
      tx.add( synth.newMsg( synthDef.name, target.node, args, addAction ), Some( (RequiresChange, isOnline, true) ),
              audible = true, dependancies = deps ++ Map( target.isOnline -> true, synthDef.isOnline -> true ))
   }
}

object RichGroup {
   def apply( group: Group ) : RichGroup = new RichGroup( group, false )
   def default( server: Server ) : RichGroup = new RichGroup( server.defaultGroup, true ) // not very fortunate XXX
}

/**
 *    @todo needs unapply and equals?
 */
class RichGroup private( val group: Group, initOnline: Boolean ) extends RichNode( initOnline ) {
   def node: Node = group

   override def toString = "RichGroup(" + group.toString + ")"

   def play( target: RichNode, addAction: AddAction = addToHead )( implicit tx: ProcTxn ) {
      require( target.server == server )

      // XXX THERE IS CURRENTLY A PROBLEM EXHIBITED BY TEST3: BASICALLY --
      // since newMsg is not audible, it might be placed in the first bundle, but then
      // since moveAfterMsg is audible, the target of this group's newMsg might be
      // moved, ending up in moveAfterMsg following the g_new message, leaving this
      // group in the wrong place of the graph.
      //
      // We thus try out a workaround by declaring a group's newMsg also audible...
//      tx.add( group.newMsg( target.node, addAction ), Some( (RequiresChange, isOnline, true) ), false,
//              Map( target.isOnline -> true ))
      tx.add( group.newMsg( target.node, addAction ), Some( (RequiresChange, isOnline, true) ), audible = true,
              dependancies = Map( target.isOnline -> true ))
   }
}

object RichSynthDef {
   def apply( server: Server, graph: SynthGraph )( implicit tx: ProcTxn ) : RichSynthDef =
      ProcDemiurg.getSynthDef( server, graph )
}

case class RichSynthDef( server: Server, synthDef: SynthDef ) extends RichObject {
   val isOnline: RichState = new RichState( this, "isOnline", false )

   def name : String = synthDef.name

   /**
    *    Actually checks if the def is already online.
    *    Only if that is not the case, the receive message
    *    will be queued.
    */
   def recv( implicit tx: ProcTxn ) {
      tx.add( synthDef.recvMsg, Some( (IfChanges, isOnline, true) ), audible = false )
   }

   def play( target: RichNode, args: Seq[ ControlSetMap ] = Nil,
             addAction: AddAction = addToHead, bufs: Seq[ RichBuffer ] = Nil )( implicit tx: ProcTxn ) : RichSynth = {
      recv  // make sure it is online
      val synth   = Synth( server )
      val rs      = RichSynth( synth, this )
      rs.play( target, args, addAction, bufs )
      rs
   }
}

class RichState( obj: AnyRef, name: String, init: Boolean ) {
   private val value = Ref( init )
//   def isSatisfied( value: Boolean )( implicit tx: ProcTxn ) : Boolean = this.value() == value
//   def currentState( implicit tx: ProcTxn ) : AnyRef
   def swap( newValue: Boolean )( implicit tx: ProcTxn ) : Boolean = value.swap( newValue )
   def get( implicit tx: ProcTxn ) : Boolean = value.apply
   def set( newValue: Boolean )( implicit tx: ProcTxn ) { value.set( newValue )}

   override def toString = "<" + obj.toString + " " + name + ">"
}