package com.rockhard.pcfilesharing

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {
    val currentDirName: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val currentAppUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    init {
        currentDirName.value = SpUtil.getString(SpUtil.FOLDER_URI, "")
        currentAppUrl.value = getAppUrl()
    }

    private fun getAppUrl(): String{
        return "${SpUtil.HTTP_PROTOCOL}${SpUtil.getIpv4HostAddress()}:${SpUtil.PORT}/"
    }
    fun setRootDir(rootDir: String){
        SpUtil.storeString(SpUtil.FOLDER_URI, rootDir)
        viewModelScope.launch {
            currentDirName.value = rootDir
        }
    }
}