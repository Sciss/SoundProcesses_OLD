/*
 *  GraphBuilderImpl.scala
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
import collection.immutable.{ Queue => IQueue }
import de.sciss.synth.proc.{ Proc, ProcBuffer, ProcGraphBuilder, ProcParamAudioInput,
   ProcParamAudioOutput, ProcParamFloat, ProcRunning, ProcSynthReaction, ProcTxn,
   RichAudioBus, RichControlBus, RichSynth, RichSynthDef }
import de.sciss.synth.{ ControlSetMap, SynthGraph }
import de.sciss.synth.io.{AudioFileType, SampleFormat}

class GraphBuilderImpl( graph: GraphImpl, val tx: ProcTxn )
extends EntryBuilderImpl with ProcGraphBuilder {
   private var buffers     = Set.empty[ ProcBuffer ]
   private var reactions   = Set.empty[ ProcSynthReaction ]
   private var bufCount    = 0
   private var indivCount  = 0

   def includeBuffer( b: ProcBuffer ) {
      buffers += b
   }

   def individuate: Int = {
      val res = indivCount
      indivCount += 1
      res
   }

   def bufEmpty( numFrames: Int, numChannels: Int ) : ProcBuffer = {
      val unique = bufCount
      bufCount += 1
      new BufferEmptyImpl( unique, numFrames, numChannels )
   }

   def bufCue( path: String, startFrame: Long ) : ProcBuffer = {
      val unique = bufCount
      bufCount += 1
      new BufferCueImpl( unique, path, startFrame )
   }

   def bufRecord( path: String, numChannels: Int, fileType: AudioFileType, sampleFormat: SampleFormat ) : ProcBuffer = {
      val unique = bufCount
      bufCount += 1
      new BufferRecordImpl( unique, path, numChannels, fileType, sampleFormat )
   }

   def includeReaction( r: ProcSynthReaction ) {
      reactions += r
   }

   /**
    */
   def play : ProcRunning = {
      implicit val t = tx
      ProcGraphBuilder.use( this ) {
         val p             = Proc.local
         val g             = SynthGraph( graph.eval() )

         val server        = p.server
         val rsd           = RichSynthDef( server, g )
         val bufSeq        = buffers.toSeq

         var setMaps       = Vector.empty[ ControlSetMap ]  // warning: rs.newMsg doesn't support setn style! XXX
         var accessories   = IQueue.empty[ RichSynth => AudioBusPlayerImpl ]

         usedParams.foreach( _ match {
            case pFloat: ProcParamFloat => {
               val name = pFloat.name
               val cv   = p.control( name ).cv
               cv.mapping match {
                  case None => setMaps :+= ControlSetMap.Single( name, cv.target.toFloat )
                  case Some( m ) => m.mapBus match {
                     case rab: RichAudioBus => {
                        accessories = accessories.enqueue( rs => AudioBusPlayerImpl( m, rs.map( rab -> name )))
//                        audioMappings  = audioMappings.enqueue( rab -> name )
                     }
                     case rcb: RichControlBus => {
                        println( "WARNING: Mapping to control bus not yet supported" )
                        setMaps :+= ControlSetMap.Single( name, cv.target.toFloat )
                     }
                  }
               }
            }
            case pAudioBus: ProcParamAudioInput => {
               val name       = pAudioBus.name
               val b          = p.audioInput( name )
               val rab        = b.bus.get
               accessories    = accessories.enqueue( rs => AudioBusPlayerImpl( b, rs.read( rab -> name )))
//               accessories    = accessories.enqueue( b )
//               audioInputs    = audioInputs.enqueue( b.bus.get -> name )
            }
            case pAudioBus: ProcParamAudioOutput => {
               val name       = pAudioBus.name
               val b          = p.audioOutput( name )
               val rab        = b.bus.get
               accessories    = accessories.enqueue( rs => AudioBusPlayerImpl( b, rs.write( rab -> name )))
//               accessories    = accessories.enqueue( b )
//               audioOutputs   = audioOutputs.enqueue( b.bus.get -> name )
            }
            case x => println( "Ooops. what parameter is this? " + x ) // scalac doesn't check exhaustion...
         })

         val (target, addAction) = p.runningTarget( requireGroup = false )
         val bufs          = bufSeq.map( _.create( server ))
         val bufsZipped    = bufSeq.zip( bufs )
         setMaps ++= bufsZipped.map( tup => ControlSetMap.Single( tup._1.controlName, tup._2.buf.id ))
         val rs = rsd.play( target, setMaps, addAction, bufs )
         val morePlayers   = reactions.map( _.create( rs ))

         val accMap: Map[ String, AudioBusPlayerImpl ] = accessories.map( fun => {
            val abp = fun( rs )
//println( "acc : " + abp )
            abp.player.play // stop is in RunningGraphImpl
            abp.setter.controlName -> abp
         })( breakOut )

         bufsZipped foreach { tup =>
            val (b, rb) = tup
// DEBUG
//println( "disposeWith : " + b )
            b.disposeWith( rb, rs )        // XXX should also go in RunningGraphImpl
         }
         new RunningGraphImpl( rs, accMap, morePlayers )
      }
   }
}