package com.nethome.controller

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nethome.controller.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<Device>()

    fun submitList(newItems: List<Device>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: Device, onClick: (Device) -> Unit) {
            binding.textName.text = device.displayName
            binding.textDetail.text = device.subtitle
            binding.textType.text = device.type.name.replace("_", " ")
            binding.statusDot.setBackgroundResource(
                if (device.isOnline) R.drawable.dot_online else R.drawable.dot_offline
            )
            binding.root.setOnClickListener { onClick(device) }
        }
    }
}
