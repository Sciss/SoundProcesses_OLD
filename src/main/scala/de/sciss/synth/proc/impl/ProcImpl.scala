/*
 *  ProcImpl.scala
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

package de.sciss.synth.proc.impl

import collection.breakOut
import collection.immutable.{ IndexedSeq => IIdxSeq, Set => ISet }
import de.sciss.synth
import de.sciss.synth.{ addToHead, addAfter, addBefore, addToTail,
   audio => arate, control => krate, scalar => irate, AddAction, Group, Server, SynthGraph }
import de.sciss.synth.ugen.Line
import de.sciss.synth.proc.{ ControlValue, Glide, Instant, Proc, ProcAudioBus, ProcAudioInput,
   ProcAudioOutput, ProcControl, ProcDemiurg, ProcEdge, ProcParam,
   ProcParamAudio, ProcParamControl, ProcParamScalar, ProcRunning, ProcTxn, Ref,
   RichAudioBus, RichGroup, RichNode, RichSynthDef, XFade }
import sys.error

/**
 *    @version 0.14, 02-Aug-10
 */
class ProcImpl( fact: FactoryImpl, cnt: Int, val server: Server )
extends Proc {
   proc =>

   import Proc._

   private val runningRef        = Ref[ Option[ ProcRunning ]]( None )
//   private val groupVar          = Ref[ Option[ RichGroup ]]( None )
//   private val playGroupVar      = Ref[ Option[ RichGroup ]]( None )
   private val groupsRef         = Ref[ Option[ AllGroups ]]( None )
//   private val pStringValues     = Ref( Map.empty[ ProcParamString, String ])
   private val stateRef          = Ref( State( valid = true ))
   private val backRef           = Ref[ Option[ RichGroup ]]( None )

   lazy val audioInputs          = fact.pAudioIns.map(  p => new AudioInputImpl(  this, p ))
   lazy val audioOutputs         = fact.pAudioOuts.map( p => new AudioOutputImpl( this, p ))
   lazy val controls             = fact.params.collect {
      case pControl: ProcParamControl  => new ControlImpl( proc, pControl, krate )
      case pAudio: ProcParamAudio      => new ControlImpl( proc, pAudio, arate )
      case pScalar: ProcParamScalar    => new ControlImpl( proc, pScalar, irate )
   }
   private lazy val controlMap: Map[ String, ProcControl ] = controls.map( c => (c.name -> c) )( breakOut )

//   private[proc] def init( implicit tx: ProcTxn ) {
//      state = State( true ) // state.copy( valid = true )
//   }

   def name    = if( cnt > 1 ) (fact.name + "#" + cnt) else fact.name
   def anatomy = fact.anatomy

   def audioInput( name: String ) : ProcAudioInput    = audioInputs.find(  _.name == name ).get
   def audioOutput( name: String ) : ProcAudioOutput  = audioOutputs.find( _.name == name ).get

   def param( name: String ) : ProcParam = fact.paramMap( name )
   def params : IIdxSeq[ ProcParam ] = fact.params

   def control( name: String ) : ProcControl = controlMap( name )

   // XXX should have the edgeMap directly here instead of spread
   // across the buses!!
   def outEdges( implicit tx: ProcTxn ) : ISet[ ProcEdge ] = {
      audioOutputs.flatMap( _.edges )( breakOut )
   }

//   def setString( name: String, value: String )( implicit tx: ProcTxn ) : Proc = {
//      val p = fact.paramMap( name ).asInstanceOf[ ProcParamString ]
//      pStringValues.transform( _ + (p -> value) )
//      runningRef().foreach( _.setString( name, value ))
//      this
//   }
//
//    def getString( name: String )( implicit tx: ProcTxn ) : String = {
//       val p = fact.paramMap( name ).asInstanceOf[ ProcParamString ]
////          pStringValues().get( p ).getOrElse( p.default.getOrElse( pError( name )))
//       pStringValues().get( p ).getOrElse( pError( name ))
//   }

//   def runningGroup( implicit tx: ProcTxn ) : RichGroup =
//      groupsRef().map( all => all.front.map( _.core ).getOrElse( all.main )).getOrElse( RichGroup.default( server ))

   def runningTarget( requireGroup: Boolean )( implicit tx: ProcTxn ) : (RichNode, AddAction) = {
      groupsRef() map { all =>
         all.core map { core =>
            core -> addToHead
         } getOrElse { all.pre map { pre =>
            pre -> addAfter
         } getOrElse { all.post map { post =>
            post -> addBefore
         } getOrElse {
            all.main -> addToTail
         }}}
      } getOrElse {
         RichGroup.default( server ) -> addToHead
      }
   }

//   private def pError( name: String ) = throw new ProcParamUnspecifiedException( name )

   def groupOption( implicit tx: ProcTxn ) : Option[ RichGroup ] = groupsRef().map( _.main )

   def group( implicit tx: ProcTxn ) : RichGroup = {
      groupOption getOrElse {
         val g    = Group( server )
         val res  = RichGroup( g )
         res.play( RichGroup.default( server ))
         group    = res
         res
      }
   }

//   // callers should know what they are doing... XXX
//   def backGroup( implicit tx: ProcTxn ) : RichGroup = groupsRef().get.back.get
//
//   def group_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
//      groupsRef.transform( _ map { all =>
//         all.front.foreach( f => {
//            f.core.moveToTail( true, newGroup )
//            f.pre.foreach(  _.moveBefore( true, f.core ))
//            f.post.foreach( _.moveAfter(  true, f.core ))
//         })
//         all.back.foreach( _.moveToHead( true, newGroup ))
//         all.main.free( true ) // que se puede...?
//         all.copy( main = newGroup )
//      } orElse {
//         val all = AllGroups( newGroup, None, None )
//         runningRef().foreach( _.setGroup( newGroup ))
//         Some( all )
//      })
//   }

   def group_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
      groupsRef.transform( _ map { all =>
         moveAllTo( all, newGroup )
         all.main.free( audible = true ) // que se puede...?
         all.copy( main = newGroup )
      } orElse {
         val all = AllGroups( newGroup, None, None, None, None )
         runningRef().foreach( _.setGroup( newGroup ))
         Some( all )
      })
   }

   private def moveAllTo( all: AllGroups, newGroup: RichGroup )( implicit tx: ProcTxn ) {
      anchorNodeOption map { core =>
         core.moveToTail( audible = true, newGroup )
         all.pre.foreach(  _.moveBefore( audible = true, core ))
         all.post.foreach( _.moveAfter(  audible = true, core ))
      } getOrElse {
         all.post.map { post =>
            post.moveToTail( audible = true, newGroup )
            all.pre.foreach( _.moveBefore( audible = true, post ))
         } getOrElse {
            all.pre.foreach( _.moveToTail( audible = true, newGroup ))
         }
      }
      all.back.foreach( _.moveToHeadIfOnline( newGroup )) // ifOnline !
   }

   private def coreGroupOption( implicit tx: ProcTxn ) : Option[ RichGroup ] = groupsRef().flatMap( _.core )

   def coreGroup( implicit tx: ProcTxn ) : RichGroup = {
      coreGroupOption getOrElse {
         val g       = Group( server )
         val res     = RichGroup( g )
         val main    = group       // creates group if necessary
         val all     = groupsRef().get
         val (target, addAction) = all.pre map { pre =>
            pre -> addAfter
         } getOrElse { all.post map { post =>
            post -> addBefore
         } getOrElse {
            main -> addToTail
         }}
         res.play( target, addAction )
         runningRef().foreach( _.setGroup( res ))
         groupsRef.set( Some( all.copy( core = Some( res ))))
         res
      }
   }

   private def preGroupOption( implicit tx: ProcTxn ) : Option[ RichGroup ] = groupsRef().flatMap( _.pre )

   def preGroup( implicit tx: ProcTxn ) : RichGroup = {
      preGroupOption getOrElse {
         val g       = Group( server )
         val res     = RichGroup( g )
         val main    = group       // creates group if necessary
         val all     = groupsRef().get
         val (target, addAction) = anchorNodeOption map { core =>
            core -> addBefore
         } getOrElse { all.post map { post =>
            post -> addBefore
         } getOrElse {
            main -> addToTail
         }}
         res.play( target, addAction )
         groupsRef.set( Some( all.copy( pre = Some( res ))))
         res
      }
   }

//   private def preGroup_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
//      groupsRef transform { allO =>
//         val all     = allO.get
//         val front   = all.front.get
//         require( front.pre.isEmpty )
//         Some( all.copy( front = Some( front.copy( pre = Some( newGroup )))))
//      }
//   }

   private def anchorNodeOption( implicit tx: ProcTxn ) : Option[ RichNode ] =
      groupsRef().flatMap( _.core ) orElse runningRef().map( _.anchorNode )

   def anchorNode( implicit tx: ProcTxn ) : RichNode = anchorNodeOption getOrElse coreGroup

   private def postGroupOption( implicit tx: ProcTxn ) : Option[ RichGroup ] = groupsRef().flatMap( _.post )

   def postGroup( implicit tx: ProcTxn ) : RichGroup = {
      postGroupOption getOrElse {
         val g       = Group( server )
         val res     = RichGroup( g )
         val main    = group       // creates group if necessary
         val all     = groupsRef().get
         val (target, addAction) = anchorNodeOption map { core =>
            core -> addAfter
         } getOrElse { all.pre map { pre =>
            pre -> addAfter
         } getOrElse {
            main -> addToTail
         }}
         res.play( target, addAction )
         groupsRef.set( Some( all.copy( post = Some( res ))))
         res
      }
   }

//   private def postGroup_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
//      groupsRef transform { allO =>
//         val all     = allO.get
//         val front   = all.front.get
//         require( front.post.isEmpty )
//         Some( all.copy( front = Some( front.copy( post = Some( newGroup )))))
//      }
//   }

//   private def backGroupOption( implicit tx: ProcTxn ) : Option[ RichGroup ] =
//      groupsRef().flatMap( _.back )
//
//   private def backGroup( implicit tx: ProcTxn ) : RichGroup = {
//      backGroupOption getOrElse {
//         val g       = Group( server )
//         val res     = RichGroup( g )
//         res.play( group, addToHead ) // creates group if necessary
//         backGroup   = res
//         res
//      }
//   }
//
//   private def backGroup_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
//      groupsRef transform { allO =>
//         val all = allO.get
//         require( all.back.isEmpty )
//         Some( all.copy( back = Some( newGroup )))
//      }
//   }
//
//   private def backGroup_=( newGroup: RichGroup )( implicit tx: ProcTxn ) {
//      groupsRef transform { allO =>
//         val all = allO.get
//         require( all.back.isEmpty )
//         Some( all.copy( back = Some( newGroup )))
//      }
//   }

//   private[proc] def disconnect( out: ProcAudioOutput, in: ProcAudioInput ) {
//      error( "NOT YET IMPLEMENTED" )
//   }
//
//   private[proc] def insert( out: ProcAudioOutput, in: ProcAudioInput,
//                             insert: (ProcAudioInput, ProcAudioOutput) ) {
//      error( "NOT YET IMPLEMENTED" )
//   }

   private def createBackground( xfade: XFade, dispose: Boolean )( implicit tx: ProcTxn ) {
//      if( !xfade.markSendToBack( this )) return
//println( "createBackground" + this )

      val main    = group           // ensures that main group exists,
      val all     = groupsRef().get // i.e. that this is valid
      val back    = RichGroup( Group( server ))
      back.play( main, addToHead )
      moveAllTo( all, back )

      // XXX graphs like this could be lazy vals
      // in the companion object
      val rsd     = RichSynthDef( server, SynthGraph {
         import synth._
         Line.kr( dur = "$dur".ir, doneAction = freeGroup )
      })
      /* val rs = */ rsd.play( if( dispose ) main else back, List( "$dur" -> xfade.dur ))

      // monitor fading
      backRef.set( Some( back ))
      back.onEndTxn { tx0 =>
         if( backRef()( tx0 ) == Some( back )) {
            state_=( state( tx0 ).copy( fading = false ))( tx0 )
            backRef.set( None )( tx0 )
         }
      }

      // update groups
      groupsRef.set( if( dispose ) None else Some( AllGroups( main, None, None, None, Some( back ))))
      state = state.copy( fading = true )
   }

   def sendToBack( xfade: XFade )( implicit tx: ProcTxn ) {
      runningRef() foreach { r =>
         if( !xfade.isMarked( this )) {
            stop( replay = true )
//            createBackground( xfade, false )
//         play
         }
      }
   }

//   def bypassed( implicit tx: ProcTxn ) = bypassedRef()
//   def bypassed_=( value: Boolean )( implicit tx: ProcTxn ) {
//      val was = bypassedRef.swap( value )
////println( "BYPASSED OLD = " + was + "; NEW = " + value + "; isPlaying? " + isPlaying )
//      if( (was != value) && isPlaying ) {
//         stop
//         play
//      }
//   }

   def bypass( implicit tx: ProcTxn ) {
      setBypass( true )
   }

   def engage( implicit tx: ProcTxn ) {
      setBypass( false )
   }
   
   private def setBypass( value: Boolean )( implicit tx: ProcTxn ) {
      val st = state
      if( st.bypassed == value ) return
      if( st.playing ) stop
      state = state.copy( bypassed = value )
      if( st.playing ) play
   }

   def play( implicit tx: ProcTxn ) {
      val st = state
      if( st.playing ) {
         println( "WARNING: Proc.play - '" + this + "' already playing")
      } else {
         val entry = if( st.bypassed ) fact.bypassGraph else fact.entry
         tx.transit match {
            case xfade: XFade => coreGroup // enforce XXX ugly
            case _ =>
         }
         val run = Proc.use( proc ) {
//               val target = playGroupOption.getOrElse( groupOption.getOrElse( RichGroup.default( server )))
            entry.play
         }
         runningRef.set( Some( run ))
         state = st.copy( playing = true )
      }
   }

   def state( implicit tx: ProcTxn ) = stateRef()
   private def state_=( newState: State )( implicit tx: ProcTxn ) {
      val oldState = stateRef.swap( newState )
      if( oldState != newState ) {
         touch
         val u = updateRef()
//println( this.toString + " :: OLD STATE : " + oldState + " -> NEW STATE : " + newState )
         updateRef.set( u.copy( state = newState ))
      }
   }

   def stop( implicit tx: ProcTxn ) {
      stop( replay = false )
   }

   private def stop( replay: Boolean )( implicit tx: ProcTxn ) {
      runningRef() foreach { r =>
         r.stop
         tx.transit match {
            case xfade: XFade => {
               xfade.markSendToBack( this, replay )
               createBackground( xfade, dispose = false )   // ok to do this repeatedly (might be even necessary)
            }
            case _ =>
         }
         runningRef.set( None )
      }
      state = state.copy( playing = false )
   }

   private def disposeEdges( implicit tx: ProcTxn ) {
      audioInputs.foreach( _.dispose )
      audioOutputs.foreach( _.dispose )
   }

   def dispose( implicit tx: ProcTxn ) {
      runningRef() map { r =>
         r.stop
// do this here, because otherwise disposeEdges will call into busChanged and runningRef()
// will forward this to the (already stopped) running which in turn will complain that
// a used bus is set to None
runningRef.set( None )
         disposeEdges   // important to do this after r.stop to avoid re-creating physical outputs!
         tx.transit match {
            case Instant => {
               groupsRef() foreach { all =>
                  all.main.free()
                  groupsRef.set( None )
               }
            }
            case xfade: XFade => {
               error( "NOT YET SUPPORTED" )
//               xfade.markSendToBack( this, false )
//               createBackground( xfade, true )
            }
            case glide: Glide => {
               error( "NOT YET SUPPORTED" )
            }
         }
//         audioInputs.foreach( _.dispose )
//println( "WARNING : Proc.dispose : STILL INCOMPLETE : EDGES ARE NOT YET REMOVED" )
//         runningRef.set( None )
      } getOrElse {
         disposeEdges
         groupsRef() foreach { all =>
            all.main.free()
            groupsRef.set( None )
         }
      }
      ProcDemiurg.removeVertex( proc )
      state = state.copy( valid = false )
   }

//   def isPlaying( implicit tx: ProcTxn ) : Boolean = runningRef().isDefined
   def isPlaying( implicit tx: ProcTxn ) : Boolean = stateRef().playing

   private[proc] def controlChanged( ctrl: ProcControl, newValue: ControlValue )( implicit tx: ProcTxn ) {
      runningRef().foreach( _.controlChanged( ctrl, newValue ))
      touch
      updateRef.transform( u => u.copy( controls = u.controls + (ctrl -> newValue) ))
   }

   private[proc] def audioBusConnected( e: ProcEdge )( implicit tx: ProcTxn ) {
      touch
      updateRef.transform( u => if( u.audioBusesDisconnected.contains( e )) {
         u.copy( audioBusesDisconnected = u.audioBusesDisconnected - e )
      } else {
         u.copy( audioBusesConnected = u.audioBusesConnected + e )
      })
   }

   private[proc] def audioBusDisconnected( e: ProcEdge )( implicit tx: ProcTxn ) {
      touch
      updateRef.transform( u => if( u.audioBusesConnected.contains( e )) {
         u.copy( audioBusesConnected = u.audioBusesConnected - e )
      } else {
         u.copy( audioBusesDisconnected = u.audioBusesDisconnected + e )
      })
   }

   private[proc] def busChanged( pbus: ProcAudioBus, newBus: Option[ RichAudioBus ])( implicit tx: ProcTxn ) {
      runningRef().foreach( _.busChanged( pbus, newBus ))
   }

   override def toString = "proc(" + name + ")"

//   private case class FrontGroups( core: Option[ RichGroup ], pre: Option[ RichGroup ], post: Option[ RichGroup ])
//   private case class AllGroups( main: RichGroup, front: FrontGroups, back: Option[ RichGroup ])
   private case class AllGroups( main: RichGroup, pre: Option[ RichGroup ], core: Option[ RichGroup ],
                                 post: Option[ RichGroup ], back: Option[ RichGroup ])
}