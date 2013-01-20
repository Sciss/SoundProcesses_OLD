/*
 *  FactoryBuilderImpl.scala
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

import de.sciss.synth.proc.{ ParamSpec, Proc, ProcAnatomy, ProcDiff, ProcFactory, ProcFactoryBuilder,
   ProcEntry, ProcFilter, ProcGen, ProcGraph, ProcIdle, ProcParam, ProcParamAudio, ProcParamAudioInput,
   ProcParamAudioOutput, ProcParamControl, ProcParamScalar, RichAudioBus }
import de.sciss.synth.ugen.In
import sys.error
import de.sciss.synth.GraphFunction

object FactoryBuilderImpl {
   def gen( name: String ) : ProcFactoryBuilder =
      new FactoryBuilderImpl( name, ProcGen, false, true )

   def filter( name: String ) : ProcFactoryBuilder =
      new FactoryBuilderImpl( name, ProcFilter, true, true )

   def diff( name: String ) : ProcFactoryBuilder =
      new FactoryBuilderImpl( name, ProcDiff, true, false )
}

class FactoryBuilderImpl protected( val name: String, val anatomy: ProcAnatomy,
                           implicitAudioIn: Boolean, implicitAudioOut: Boolean )
extends ProcFactoryBuilder {
   protected var finished                   = false
   protected var paramMap                   = Map.empty[ String, ProcParam ]
   protected var paramSeq                   = Vector.empty[ ProcParam ]
//   private var buffers                    = Map[ String, ProcBuffer ]()
//   private var graph: Option[ ProcGraph ] = None
   protected var entry: Option[ ProcEntry ] = None
   protected var pAudioIns                  = Vector.empty[ ProcParamAudioInput ]
   protected var pAudioOuts                 = Vector.empty[ ProcParamAudioOutput ]

   @inline protected def requireOngoing() { require( !finished, "ProcFactory build has finished" )}

   def pScalar( name: String, spec: ParamSpec, default: Double ) : ProcParamScalar = {
      requireOngoing()
      val p = new ParamScalarImpl( name, spec, default )
      addParam( p )
      p
   }

   def pControl( name: String, spec: ParamSpec, default: Double ) : ProcParamControl = {
      requireOngoing()
      val p = new ParamControlImpl( name, spec, default )
      addParam( p )
      p
   }

   def pAudio( name: String, spec: ParamSpec, default: Double ) : ProcParamAudio = {
      requireOngoing()
      val p = new ParamAudioImpl( name, spec, default )
      addParam( p )
      p
   }

//   def pString( name: String, default: Option[ String ]) : ProcParamString = {
//      requireOngoing
//      val p = new ParamStringImpl( name, default )
//      addParam( p )
//      p
//   }

   def pAudioIn( name: String, default: Option[ RichAudioBus ]) : ProcParamAudioInput = {
      requireOngoing()
      pAudioIn( name, default, physical = false )
   }

   protected def pAudioIn( name: String, default: Option[ RichAudioBus ], physical: Boolean ) : ProcParamAudioInput = {
      val p = new ParamAudioInputImpl( name, default, physical )
      addParam( p )
      pAudioIns :+= p
      p
   }

   def pAudioOut( name: String, default: Option[ RichAudioBus ]) : ProcParamAudioOutput = {
      requireOngoing()
      pAudioOut( name, default, physical = false )
   }


   def pAudioOut( name: String, default: Option[ RichAudioBus ], physical: Boolean ) : ProcParamAudioOutput = {
      val p = new ParamAudioOutputImpl( name, default, physical )
      addParam( p )
      pAudioOuts :+= p
      p
   }

   protected def implicitInAr : In = Proc.local.param( "in" ).asInstanceOf[ ProcParamAudioInput ].ar
   protected def implicitOutAr[ T ]( sig: T )( implicit res: GraphFunction.Result[ T ]) {
      res match {
         case GraphFunction.Result.In( view ) =>
            val in = view.apply( sig )
//            val rate = Rate.highest( sig.outputs.map( _.rate ): _* )
//            if( (rate == audio) || (rate == control) ) {
               Proc.local.param( "out" ).asInstanceOf[ ProcParamAudioOutput ].ar( in )
//            }
         case _ =>
      }
   }
   protected def implicitInNumCh : Int = Proc.local.param( "in" ).asInstanceOf[ ProcParamAudioInput ].numChannels
   protected def implicitOutNumCh( n: Int ) { Proc.local.param( "out" ).asInstanceOf[ ProcParamAudioOutput ].numChannels_=( n )}

   def graphIn( fun: In => Any ) : ProcGraph = {
      val fullFun = () => { fun( implicitInAr ); () }
      fullGraph( fullFun )
   }

   def graphInOut[ T : GraphFunction.Result ]( fun: In => T ) : ProcGraph = {
      val fullFun = () => {
         val in   = implicitInAr
         val out  = fun( in )
         implicitOutAr( out )
      }
      fullGraph( fullFun )
   }

   def graphOut[ T : GraphFunction.Result ]( thunk: => T ) : ProcGraph = {
      val fullFun = () => {
// println( "graphOut " + name )
         implicitOutAr( thunk )
      }
      fullGraph( fullFun )
   }

   def graph( thunk: => Any ) : ProcGraph = {
      fullGraph( () => thunk )
   }

   private def fullGraph( fun: () => Unit ) : ProcGraph = {
      requireOngoing()
      val res = new GraphImpl( fun )
      enter( res )
      res
   }

   def idleIn( fun: Int => Any ) : ProcIdle = {
      val fullFun: () => Unit = () => fun( implicitInNumCh )
      idle( fullFun )
   }
   
   def idleOut( fun: () => Int ) : ProcIdle = {
      val fullFun = () => implicitOutNumCh( fun() )
      idle( fullFun )
   }

   def idleInOut( fun: Int => Any ) : ProcIdle = {
      val fullFun = () => {
         val inCh = implicitInNumCh
         fun( inCh ) match {
            case outCh: Int => implicitOutNumCh( outCh )
            case _ => error( "Idle in this context requires an Int result (number of output channels)" )
         }
      }
      idle( fullFun )
   }

   def idle : ProcIdle = {
      idle( () => () )
   }
   
   protected def idle( fun: () => Unit ) : ProcIdle = {
      requireOngoing()
      val res = new IdleImpl( fun )
      enter( res )
      res
   }

   protected def enter( e: ProcEntry ) {
      require( entry.isEmpty, "Entry already defined" )
      entry = Some( e )
   }

   def finish : ProcFactory = {
      requireOngoing()
      require( entry.isDefined, "No entry point defined" )
      if( implicitAudioIn && !paramMap.contains( "in" )) {
//         pAudioIn( "in", None, true )
         pAudioIn( "in", None, physical = false )
      }
//println( "implicitAudioOut = " + implicitAudioOut + "; params.contains( \"out\" ) = " + params.contains( "out" ))
      if( implicitAudioOut && !paramMap.contains( "out" )) {
         pAudioOut( "out", None, physical = true )
      }
      finished = true
      new FactoryImpl( name, anatomy, entry.get, paramMap, paramSeq, pAudioIns, pAudioOuts )
   }

   protected def addParam( p: ProcParam ) {
      require( !paramMap.contains( p.name ), "Param name '" + p.name + "' already taken" )
      paramMap  += p.name -> p
      paramSeq :+= p
   }

//   private def addBuffer( b: ProcBuffer ) {
//      require( !buffers.contains( b.name ), "Buffer name '" + b.name + "' already taken" )
//      buffers += b.name -> b
//   }
}