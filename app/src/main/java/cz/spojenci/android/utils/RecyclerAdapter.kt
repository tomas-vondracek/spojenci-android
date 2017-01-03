package cz.spojenci.android.utils

import android.content.Context
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

abstract class RecyclerAdapter<VH : RecyclerView.ViewHolder, out T>(protected val context: Context,
                                                                    protected val items: List<T>) : RecyclerView.Adapter<VH>() {

	private val inflater = LayoutInflater.from(context)

	protected fun <B : ViewDataBinding> bindingForLayout(layoutId: Int, parent: ViewGroup): B {
		return DataBindingUtil.inflate(inflater, layoutId, parent, false)
	}

	protected fun getItem(position: Int): T {
		return items[position]
	}

	override fun getItemCount(): Int {
		return items.size
	}

}

open class BoundViewHolder<out B : ViewDataBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root)
