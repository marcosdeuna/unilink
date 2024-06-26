package com.marcosdeuna.unilink.ui.onboarding.screens

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.marcosdeuna.unilink.R

class FirstOBSFragment : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_first_o_b_s, container, false)

        val next = view.findViewById<Button>(R.id.nextbtn)
        val skip = view.findViewById<Button>(R.id.skipButton)

        val viewPager = activity?.findViewById<ViewPager2>(R.id.slideViewPager)

        next.setOnClickListener {
            viewPager?.currentItem = 1
        }

        skip.setOnClickListener {
            findNavController().navigate(R.id.action_onboardingFragment_to_loginFragment)

            onBoardingFinished()
        }

        return view
    }

    private fun onBoardingFinished() {
        val sharedPref = requireActivity().getSharedPreferences("onBoarding", Context.MODE_PRIVATE)
        val edit = sharedPref.edit()
        edit.putBoolean("Finished", true)
        edit.apply()
    }
}